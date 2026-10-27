package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAsset;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClient;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmServiceAgreement;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmAssetRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmCreditNoteRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmInvoiceRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmAssetReliabilityResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmDashboardResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSlaPortfolioResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmVendorPerformanceResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.MaintenanceAnalytics;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Management reporting for the provider: a live dashboard, subcontractor performance, asset reliability and a
 * portfolio-wide SLA view. Everything is computed on request from the work orders and invoices themselves, inside the
 * caller's access scope, using the same {@link FmSlaStatus} rules as the API and the SLA monitor so reports cannot
 * disagree with what users see.
 * <ul>
 *   <li>Field technicians get the dashboard for their own jobs only; vendor, asset and portfolio reports are
 *       management views and refuse them (403 {@code SCOPE_RESTRICTED}).</li>
 *   <li>Finance KPIs and every internal-cost figure need a cost/billing authority; without one they are omitted
 *       (null), not zero.</li>
 * </ul>
 * Reports load the matching work orders into memory, so periods are capped at {@value #MAX_SPAN_DAYS} days.
 */
@Service
@RequiredArgsConstructor
public class FmReportingService {

    static final long MAX_SPAN_DAYS = 366;
    static final int DEFAULT_ASSET_LIMIT = 20;
    static final int MAX_ASSET_LIMIT = 100;

    private static final List<String> OPEN_STATUSES = List.of("OPEN", "ASSIGNED", "IN_PROGRESS", "ON_HOLD", "PENDING_VERIFICATION");
    private static final Set<String> COST_AUTHORITIES = Set.of("FACILITIESMANAGEMENT_ADMIN", "FACILITIESMANAGEMENT_COST_READ",
            "FACILITIESMANAGEMENT_BILLING_MANAGE", "FACILITIESMANAGEMENT_BILLING_APPROVE");

    private final FmWorkOrderRepository workOrderRepository;
    private final FmInvoiceRepository invoiceRepository;
    private final FmCreditNoteRepository creditNoteRepository;
    private final FmAssetRepository assetRepository;
    private final FmClientRepository clientRepository;
    private final FmAccessPolicy accessPolicy;

    // ── Dashboard ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FmDashboardResponse dashboard(TenantId tenantId) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        Instant now = Instant.now();
        LocalDate today = FmTime.today();

        List<FmWorkOrder> active = workOrderRepository.findOpenInScope(tenantId, scope.workOrderClientFilterOff(),
                scope.clientIds(), scope.allJobs(), scope.technicianIds());

        long awaitingApproval = 0, overdue = 0, slaAtRisk = 0, slaBreached = 0;
        Map<String, Long> byStatus = new LinkedHashMap<>();
        OPEN_STATUSES.forEach(s -> byStatus.put(s, 0L));
        Map<String, Long> byPriority = new LinkedHashMap<>();
        FmServiceAgreement.PRIORITIES.forEach(p -> byPriority.put(p, 0L));

        for (FmWorkOrder w : active) {
            if ("REQUESTED".equals(w.getStatus())) { awaitingApproval++; continue; }
            byStatus.merge(w.getStatus(), 1L, Long::sum);
            byPriority.merge(w.getPriority(), 1L, Long::sum);
            if (w.isOverdue(today)) overdue++;
            String r = FmSlaStatus.response(w, now);
            String s = FmSlaStatus.resolution(w, now);
            if ("BREACHED".equals(r) || "BREACHED".equals(s)) slaBreached++;
            else if ("AT_RISK".equals(r) || "AT_RISK".equals(s)) slaAtRisk++;
        }
        long open = byStatus.values().stream().mapToLong(Long::longValue).sum();

        Instant since = today.minusDays(30).atStartOfDay(FmTime.ZONE).toInstant();
        Instant until = today.plusDays(1).atStartOfDay(FmTime.ZONE).toInstant();
        List<FmWorkOrder> completed = workOrderRepository.findCompletedBetweenInScope(tenantId, since, until,
                scope.workOrderClientFilterOff(), scope.clientIds(), scope.allJobs(), scope.technicianIds());
        long met = 0, breached = 0;
        List<BigDecimal> hours = new ArrayList<>();
        for (FmWorkOrder w : completed) {
            String s = FmSlaStatus.resolution(w, now);
            if ("MET".equals(s)) met++;
            else if ("BREACHED".equals(s)) breached++;
            if (w.getCompletedAt() != null && w.getCreatedAt() != null) {
                hours.add(BigDecimal.valueOf(Math.max(0, java.time.Duration.between(w.getCreatedAt(), w.getCompletedAt()).getSeconds()))
                        .divide(BigDecimal.valueOf(3600), 2, java.math.RoundingMode.HALF_UP));
            }
        }
        BigDecimal avgHours = hours.isEmpty() ? null
                : hours.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(hours.size()), 2, java.math.RoundingMode.HALF_UP);

        FmDashboardResponse.WorkOrderKpis kpis = new FmDashboardResponse.WorkOrderKpis(open,
                byStatus.get("OPEN"), byStatus.get("ON_HOLD"), overdue, awaitingApproval, byStatus.get("PENDING_VERIFICATION"),
                slaAtRisk, slaBreached, byStatus, byPriority, completed.size(), avgHours,
                MaintenanceAnalytics.compliance(met, breached));

        return new FmDashboardResponse(now, kpis, canSeeCost() && !scope.assignedOnly() ? finance(tenantId, scope, today) : null);
    }

    private FmDashboardResponse.FinanceKpis finance(TenantId tenantId, FmAccessScope scope, LocalDate today) {
        boolean all = scope.unrestricted();
        List<FmInvoice> receivables = invoiceRepository.findByStatusesInScope(tenantId, List.of("SENT", "PARTIAL"), all, scope.clientIds());
        BigDecimal outstanding = BigDecimal.ZERO, overdueAmount = BigDecimal.ZERO;
        long overdueCount = 0;
        for (FmInvoice i : receivables) {
            BigDecimal bal = i.balance();
            outstanding = outstanding.add(bal);
            if (i.getDueDate() != null && i.getDueDate().isBefore(today)) {
                overdueAmount = overdueAmount.add(bal);
                overdueCount++;
            }
        }
        BigDecimal invoiced = invoiceRepository.findIssuedBetweenInScope(tenantId, today.withDayOfMonth(1), today, all, scope.clientIds())
                .stream().map(FmInvoice::getTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        long drafts = invoiceRepository.findByStatusesInScope(tenantId, List.of("DRAFT"), all, scope.clientIds()).size();
        long approved = invoiceRepository.findByStatusesInScope(tenantId, List.of("APPROVED"), all, scope.clientIds()).size();
        long creditNotes = creditNoteRepository.countByStatusInScope(tenantId, "DRAFT", all, scope.clientIds());
        BigDecimal unbilled = workOrderRepository.sumUnbilledChargeInScope(tenantId, all, scope.clientIds());

        return new FmDashboardResponse.FinanceKpis(outstanding, overdueAmount, overdueCount, invoiced, drafts, approved,
                creditNotes, unbilled != null ? unbilled : BigDecimal.ZERO);
    }

    // ── Vendor performance ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FmVendorPerformanceResponse vendorPerformance(TenantId tenantId, LocalDate from, LocalDate to) {
        FmAccessScope scope = requireManagementScope(tenantId);
        validatePeriod(from, to);
        boolean cost = canSeeCost();
        Instant now = Instant.now();

        List<MaintenanceAnalytics.Job> jobs = createdBetween(tenantId, scope, from, to).stream()
                .filter(w -> w.getVendorId() != null)
                .map(w -> toJob(w, now, cost)).toList();

        List<FmVendorPerformanceResponse.Row> rows = MaintenanceAnalytics.vendorPerformance(jobs).stream()
                .map(v -> new FmVendorPerformanceResponse.Row(v.vendorId(), v.vendorName(), v.jobs(), v.completed(),
                        v.cancelled(), v.open(), v.avgResolutionHours(), v.reworkJobs(), v.reworkRatePercent(),
                        v.slaMet(), v.slaBreached(), v.slaCompliancePercent(),
                        cost ? v.totalCost() : null, cost ? v.avgCost() : null))
                .toList();
        return new FmVendorPerformanceResponse(from, to, rows);
    }

    // ── Asset reliability ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FmAssetReliabilityResponse assetReliability(TenantId tenantId, LocalDate from, LocalDate to, Integer limit) {
        FmAccessScope scope = requireManagementScope(tenantId);
        validatePeriod(from, to);
        int cap = limit == null ? DEFAULT_ASSET_LIMIT : Math.max(1, Math.min(MAX_ASSET_LIMIT, limit));
        boolean cost = canSeeCost();
        Instant now = Instant.now();

        Instant start = from.atStartOfDay(FmTime.ZONE).toInstant();
        if (!start.isBefore(now)) throw new IllegalArgumentException("'from' must be in the past");
        // A period that runs into the future is measured only up to now, or MTBF would be flattered by time that hasn't happened.
        Instant end = Collections.min(List.of(to.plusDays(1).atStartOfDay(FmTime.ZONE).toInstant(), now));

        List<MaintenanceAnalytics.Job> jobs = createdBetween(tenantId, scope, from, to).stream()
                .filter(w -> w.getAssetId() != null).map(w -> toJob(w, now, cost)).toList();
        List<MaintenanceAnalytics.AssetStats> stats = MaintenanceAnalytics.assetReliability(jobs, start, end, cap);

        Map<UUID, FmAsset> assets = stats.isEmpty() ? Map.of() : assetRepository.findAllById(
                        stats.stream().map(MaintenanceAnalytics.AssetStats::assetId).toList())
                .stream().collect(Collectors.toMap(FmAsset::getId, a -> a, (a, b) -> a));

        List<FmAssetReliabilityResponse.Row> rows = stats.stream().map(s -> {
            FmAsset a = assets.get(s.assetId());
            return new FmAssetReliabilityResponse.Row(s.assetId(), a != null ? a.getAssetTag() : null,
                    a != null ? a.getName() : null, a != null ? a.getSiteId() : null, s.failures(), s.repeatFailures(),
                    s.downtimeMinutes(), s.mttrHours(), s.mtbfHours(), s.topFailureCause(), cost ? s.totalCost() : null);
        }).toList();
        return new FmAssetReliabilityResponse(from, to, cap, rows);
    }

    // ── SLA across the portfolio ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FmSlaPortfolioResponse slaPortfolio(TenantId tenantId, LocalDate from, LocalDate to) {
        FmAccessScope scope = requireManagementScope(tenantId);
        validatePeriod(from, to);
        Instant now = Instant.now();

        Map<UUID, FmSlaTally> perClient = new LinkedHashMap<>();
        FmSlaTally total = new FmSlaTally();
        for (FmWorkOrder w : createdBetween(tenantId, scope, from, to)) {
            if (!w.hasSla() || "CANCELLED".equals(w.getStatus())) continue;
            perClient.computeIfAbsent(w.getClientId(), k -> new FmSlaTally()).add(w, now);
            total.add(w, now);
        }

        Map<UUID, String> names = perClient.isEmpty() ? Map.of() : clientRepository.findAllById(perClient.keySet())
                .stream().collect(Collectors.toMap(FmClient::getId, FmClient::getTradingName, (a, b) -> a));

        List<FmSlaPortfolioResponse.ClientRow> rows = new ArrayList<>();
        perClient.forEach((id, t) -> rows.add(new FmSlaPortfolioResponse.ClientRow(id, names.get(id), t.workOrders,
                t.responseMet, t.responseBreached, t.responseCompliance(),
                t.resolutionMet, t.resolutionBreached, t.resolutionCompliance())));
        // Worst first: lowest of the two compliance figures; clients with nothing concluded last.
        rows.sort(Comparator.comparing(FmSlaPortfolioResponse.ClientRow::clientName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(FmSlaPortfolioResponse.ClientRow::clientId));
        rows.sort(Comparator.comparing(FmReportingService::worstCompliance, Comparator.nullsLast(Comparator.naturalOrder())));

        return new FmSlaPortfolioResponse(from, to, total.workOrders, total.responseCompliance(), total.resolutionCompliance(), rows);
    }

    private static BigDecimal worstCompliance(FmSlaPortfolioResponse.ClientRow r) {
        BigDecimal a = r.responseCompliancePercent(), b = r.resolutionCompliancePercent();
        if (a == null) return b;
        if (b == null) return a;
        return a.min(b);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private FmAccessScope requireManagementScope(TenantId tenantId) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        if (scope.assignedOnly()) {
            throw new HandyFlowException("Not permitted for this access level", HttpStatus.FORBIDDEN, "SCOPE_RESTRICTED");
        }
        return scope;
    }

    private boolean canSeeCost() {
        return COST_AUTHORITIES.stream().anyMatch(accessPolicy::hasAuthority);
    }

    private static void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new IllegalArgumentException("'from' and 'to' are required");
        if (to.isBefore(from)) throw new IllegalArgumentException("'to' must not be before 'from'");
        if (ChronoUnit.DAYS.between(from, to) > MAX_SPAN_DAYS)
            throw new IllegalArgumentException("The reporting period cannot exceed " + MAX_SPAN_DAYS + " days");
    }

    private List<FmWorkOrder> createdBetween(TenantId tenantId, FmAccessScope scope, LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(FmTime.ZONE).toInstant();
        Instant endExclusive = to.plusDays(1).atStartOfDay(FmTime.ZONE).toInstant();
        return workOrderRepository.findCreatedBetweenInScope(tenantId, start, endExclusive,
                scope.workOrderClientFilterOff(), scope.clientIds(), scope.allJobs(), scope.technicianIds());
    }

    private static MaintenanceAnalytics.Job toJob(FmWorkOrder w, Instant now, boolean includeCost) {
        boolean reactive = !"PPM".equals(w.getCategory()) && !"INSPECTION".equals(w.getCategory());
        String sla = FmSlaStatus.resolution(w, now);
        Boolean slaMet = "MET".equals(sla) ? Boolean.TRUE : "BREACHED".equals(sla) ? Boolean.FALSE : null;
        return new MaintenanceAnalytics.Job(w.getAssetId(), w.getVendorId(), w.getVendorName(), reactive,
                "CANCELLED".equals(w.getStatus()), "COMPLETED".equals(w.getStatus()), w.getCreatedAt(), w.getCompletedAt(),
                w.getDowntimeMinutes(), w.getFailureCause(), w.getReworkCount(),
                includeCost ? w.getInternalCost() : null, slaMet);
    }
}
