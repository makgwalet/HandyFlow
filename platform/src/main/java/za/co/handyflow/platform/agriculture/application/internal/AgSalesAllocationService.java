package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgSalesAllocation;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgSalesAllocationRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgCostAllocation;
import za.co.handyflow.platform.agriculture.domain.rules.AgRevenueRules;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.AllocateSaleRequest;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SaleLineResponse;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SaleShare;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SalesAllocationResponse;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SalesTotalsResponse;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.TargetRevenue;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.SaleLine;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Links invoice lines to production (ADR-001, W2). Agriculture does not own sales: it records which part of an invoice line belongs to which
 * crop cycle, group, animal or enterprise, and computes revenue LIVE from Invoicing each time, so a cancelled invoice or a later credit note is
 * reflected instead of going stale. See {@link AgRevenueRules} for the rules and the exact apportionment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgSalesAllocationService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final int MAX_RANGE_DAYS = 366;
    private static final int DEFAULT_RANGE_DAYS = 90;
    private static final int INVOICE_LIMIT = 200;
    private static final int LINE_LIMIT = 500;
    private static final int TOTALS_LIMIT = 1000;

    private final AgSalesAllocationRepository allocationRepository;
    private final AgFarmRepository farmRepository;
    private final AgTargetOwnership targetOwnership;
    private final InvoicingFacade invoicingFacade;

    /** Invoice lines that count as revenue, issued in the range, with what is already allocated and what is left. */
    @Transactional(readOnly = true)
    public List<SaleLineResponse> searchLines(TenantId tenantId, UUID farmId, LocalDate from, LocalDate to, String text) {
        requireFarm(tenantId, farmId);
        LocalDate end = to != null ? to : LocalDate.now(SAST);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS);
        if (start.isAfter(end)) throw new IllegalArgumentException("from must not be after to");
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) throw new IllegalArgumentException("the date range can be at most " + MAX_RANGE_DAYS + " days");

        String needle = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        List<SaleLine> lines = new ArrayList<>();
        for (SaleLine l : invoicingFacade.searchSaleLines(tenantId, start.atStartOfDay(SAST).toInstant(), end.plusDays(1).atStartOfDay(SAST).toInstant(),
                AgRevenueRules.RECOGNISED_STATUSES, INVOICE_LIMIT)) {
            if (needle.isEmpty() || matches(l, needle)) lines.add(l);
            if (lines.size() >= LINE_LIMIT) break;
        }
        Map<UUID, BigDecimal> allocated = allocatedQuantities(tenantId, lines.stream().map(SaleLine::lineItemId).toList());
        List<SaleLineResponse> out = new ArrayList<>();
        for (SaleLine l : lines) {
            BigDecimal done = allocated.getOrDefault(l.lineItemId(), BigDecimal.ZERO);
            out.add(new SaleLineResponse(l.invoiceId(), l.invoiceNumber(), l.invoiceStatus(), l.customerId(), l.customerName(),
                    l.issuedAt() == null ? null : l.issuedAt().atZone(SAST).toLocalDate(), l.currency(), l.lineItemId(), l.description(), l.unit(),
                    l.quantity(), l.unitPrice(), l.lineTotal(), AgRevenueRules.netLineRevenue(l.lineTotal(), l.creditedSubtotal(), l.invoiceSubtotal()),
                    done, AgRevenueRules.remainingQuantity(l.quantity(), done)));
        }
        return out;
    }

    /** Allocates part (or all) of one invoice line to one or more production targets on this farm. All or nothing. */
    @Transactional
    public List<SalesAllocationResponse> allocate(TenantId tenantId, UUID farmId, UUID userId, AllocateSaleRequest req) {
        requireFarm(tenantId, farmId);
        SaleLine line = invoicingFacade.findSaleLine(tenantId, req.invoiceLineId())
                .orElseThrow(() -> new ResourceNotFoundException("InvoiceLine", req.invoiceLineId().toString()));
        if (!AgRevenueRules.isRecognised(line.invoiceStatus())) {
            throw new IllegalStateException("invoice " + line.invoiceNumber() + " is " + line.invoiceStatus() + ": only issued invoices count as revenue");
        }
        LocalDate today = LocalDate.now(SAST);
        LocalDate soldOn = req.soldOn() != null ? req.soldOn() : line.issuedAt() != null ? line.issuedAt().atZone(SAST).toLocalDate() : today;
        if (soldOn.isAfter(today)) throw new IllegalArgumentException("soldOn cannot be in the future");
        if (req.allocations() == null || req.allocations().isEmpty()) throw new IllegalArgumentException("at least one target is required");

        Set<String> seen = new HashSet<>();
        BigDecimal requested = BigDecimal.ZERO;
        for (SaleShare s : req.allocations()) {
            if (!seen.add(s.targetType() + ":" + s.targetId())) throw new IllegalArgumentException("the same target appears more than once; combine its quantities");
            targetOwnership.requireOnFarm(tenantId, farmId, s.targetType(), s.targetId());
            requested = requested.add(s.quantity());
        }
        AgRevenueRules.requireWithinLine(line.quantity(), allocatedQuantities(tenantId, List.of(line.lineItemId())).getOrDefault(line.lineItemId(), BigDecimal.ZERO), requested);

        List<AgSalesAllocation> created = new ArrayList<>();
        for (SaleShare s : req.allocations()) {
            created.add(AgSalesAllocation.create(tenantId, farmId, line.invoiceId(), line.lineItemId(), line.invoiceNumber(), line.description(),
                    s.targetType(), s.targetId(), s.quantity(), line.unit(), s.headCount(), soldOn, req.notes(), userId));
        }
        allocationRepository.saveAll(created);
        log.info("Sale allocated line={} invoice={} targets={} tenant={}", line.lineItemId(), line.invoiceNumber(), created.size(), tenantId.getValue());
        return toResponses(tenantId, created);
    }

    @Transactional(readOnly = true)
    public Page<SalesAllocationResponse> list(TenantId tenantId, UUID farmId, String targetType, UUID targetId, Pageable pageable) {
        requireFarm(tenantId, farmId);
        Page<AgSalesAllocation> page = targetType != null
                ? allocationRepository.findActiveForTarget(tenantId, requireValidTarget(targetType, targetId), targetId, pageable)
                : allocationRepository.findActiveForFarm(tenantId, farmId, pageable);
        List<SalesAllocationResponse> rows = toResponses(tenantId, page.getContent());
        Map<UUID, SalesAllocationResponse> byId = new HashMap<>();
        for (SalesAllocationResponse r : rows) byId.put(r.id(), r);
        return page.map(a -> byId.get(a.getId()));
    }

    /** Revenue of the farm's active allocations (or one target's), live, with a breakdown by target. */
    @Transactional(readOnly = true)
    public SalesTotalsResponse totals(TenantId tenantId, UUID farmId, String targetType, UUID targetId) {
        requireFarm(tenantId, farmId);
        Page<AgSalesAllocation> page = targetType != null
                ? allocationRepository.findActiveForTarget(tenantId, requireValidTarget(targetType, targetId), targetId, Pageable.ofSize(TOTALS_LIMIT))
                : allocationRepository.findActiveForFarm(tenantId, farmId, Pageable.ofSize(TOTALS_LIMIT));
        BigDecimal revenue = BigDecimal.ZERO;
        int notCounted = 0;
        Map<String, BigDecimal[]> byTarget = new LinkedHashMap<>();             // [quantity, revenue]
        Map<String, String[]> keys = new HashMap<>();
        for (SalesAllocationResponse r : toResponses(tenantId, page.getContent())) {
            revenue = revenue.add(r.revenue());
            if (!r.counted()) notCounted++;
            String key = r.targetType() + ":" + r.targetId();
            BigDecimal[] acc = byTarget.computeIfAbsent(key, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add(r.quantity());
            acc[1] = acc[1].add(r.revenue());
            keys.put(key, new String[] {r.targetType(), r.targetId().toString()});
        }
        List<TargetRevenue> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> e : byTarget.entrySet()) {
            rows.add(new TargetRevenue(keys.get(e.getKey())[0], UUID.fromString(keys.get(e.getKey())[1]), e.getValue()[0], e.getValue()[1]));
        }
        rows.sort((a, b) -> b.revenue().compareTo(a.revenue()));
        return new SalesTotalsResponse(revenue, page.getContent().size(), notCounted, rows);
    }

    /** Takes an allocation out of revenue. It stays on record as REMOVED. */
    @Transactional
    public void remove(TenantId tenantId, UUID id, UUID userId) {
        AgSalesAllocation a = allocationRepository.findForTenantById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("SalesAllocation", id.toString()));
        a.remove(userId);
        allocationRepository.save(a);
        log.info("Sale allocation removed id={} tenant={}", id, tenantId.getValue());
    }

    // ---- live revenue ------------------------------------------------------------------------------------------------

    private record Live(Map<UUID, BigDecimal> revenue, Map<UUID, String> notCounted, Map<UUID, SaleLine> lines) {}

    /**
     * Each line's revenue is shared across ALL its active allocations (every farm), so the cents add up exactly; allocations on an invoice
     * that is no longer revenue (cancelled, or not found) add nothing and say why.
     */
    private Live liveRevenue(TenantId tenantId, Set<UUID> lineIds) {
        Map<UUID, SaleLine> lines = new HashMap<>();
        for (SaleLine l : invoicingFacade.findSaleLines(tenantId, lineIds)) lines.put(l.lineItemId(), l);
        Map<UUID, List<AgSalesAllocation>> byLine = new LinkedHashMap<>();
        for (AgSalesAllocation a : allocationRepository.findActiveByInvoiceLines(tenantId, lineIds)) {
            byLine.computeIfAbsent(a.getInvoiceLineId(), k -> new ArrayList<>()).add(a);
        }
        Map<UUID, BigDecimal> revenue = new HashMap<>();
        Map<UUID, String> notCounted = new HashMap<>();
        for (Map.Entry<UUID, List<AgSalesAllocation>> e : byLine.entrySet()) {
            SaleLine line = lines.get(e.getKey());
            String reason = line == null ? "the invoice line could not be found"
                    : !AgRevenueRules.isRecognised(line.invoiceStatus()) ? "invoice " + line.invoiceNumber() + " is " + line.invoiceStatus() + ", so it is not revenue" : null;
            if (reason != null) {
                for (AgSalesAllocation a : e.getValue()) { revenue.put(a.getId(), BigDecimal.ZERO.setScale(2)); notCounted.put(a.getId(), reason); }
                continue;
            }
            List<BigDecimal> quantities = e.getValue().stream().map(AgSalesAllocation::getQuantity).toList();
            List<BigDecimal> parts = AgRevenueRules.apportion(
                    AgRevenueRules.netLineRevenue(line.lineTotal(), line.creditedSubtotal(), line.invoiceSubtotal()), line.quantity(), quantities);
            for (int i = 0; i < e.getValue().size(); i++) revenue.put(e.getValue().get(i).getId(), parts.get(i));
        }
        return new Live(revenue, notCounted, lines);
    }

    private List<SalesAllocationResponse> toResponses(TenantId tenantId, List<AgSalesAllocation> allocations) {
        if (allocations.isEmpty()) return List.of();
        Set<UUID> lineIds = new HashSet<>();
        for (AgSalesAllocation a : allocations) lineIds.add(a.getInvoiceLineId());
        Live live = liveRevenue(tenantId, lineIds);
        List<SalesAllocationResponse> out = new ArrayList<>();
        for (AgSalesAllocation a : allocations) {
            SaleLine line = live.lines().get(a.getInvoiceLineId());
            String reason = live.notCounted().get(a.getId());
            boolean counted = reason == null && live.revenue().containsKey(a.getId());
            out.add(new SalesAllocationResponse(a.getId(), a.getFarmId(), a.getInvoiceId(), a.getInvoiceNumber(), a.getInvoiceLineId(), a.getDescription(),
                    line == null ? null : line.customerName(), a.getTargetType(), a.getTargetId(), a.getQuantity(), a.getUnit(), a.getHeadCount(), a.getSoldOn(),
                    a.getNotes(), a.getStatus(), line == null ? null : line.invoiceStatus(),
                    counted ? live.revenue().get(a.getId()) : BigDecimal.ZERO.setScale(2), counted, reason, a.getCreatedAt()));
        }
        return out;
    }

    private Map<UUID, BigDecimal> allocatedQuantities(TenantId tenantId, List<UUID> lineIds) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        if (lineIds.isEmpty()) return out;
        for (Object[] r : allocationRepository.sumActiveQuantityByInvoiceLine(tenantId, lineIds)) {
            out.put((UUID) r[0], r[1] instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(r[1])));
        }
        return out;
    }

    private static boolean matches(SaleLine l, String needle) {
        return contains(l.invoiceNumber(), needle) || contains(l.customerName(), needle) || contains(l.description(), needle);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private void requireFarm(TenantId tenantId, UUID farmId) {
        farmRepository.findActiveById(tenantId, farmId).orElseThrow(() -> new ResourceNotFoundException("Farm", farmId.toString()));
    }

    private static String requireValidTarget(String targetType, UUID targetId) {
        if (!AgCostAllocation.TARGET_TYPES.contains(targetType)) throw new IllegalArgumentException("targetType must be one of " + AgCostAllocation.TARGET_TYPES);
        if (targetId == null) throw new IllegalArgumentException("targetId is required with targetType");
        return targetType;
    }
}
