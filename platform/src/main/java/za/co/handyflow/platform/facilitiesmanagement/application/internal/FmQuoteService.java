package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.*;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmQuoteRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmQuoteResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Staff side of quotes: prepare a draft, send it to the client (it then appears in the portal and is frozen), withdraw
 * it. The client answers in the portal ({@link FmPortalQuoteService}). Scope follows the caller's client access;
 * out-of-scope quotes read as not found. Every change is audited.
 */
@Service
@RequiredArgsConstructor
public class FmQuoteService {

    private static final Set<String> TERMINAL_WORK_ORDER = Set.of("COMPLETED", "CANCELLED", "REJECTED");

    private final FmQuoteRepository quoteRepository;
    private final FmQuoteLineRepository lineRepository;
    private final FmClientRepository clientRepository;
    private final FmSiteRepository siteRepository;
    private final FmAssetRepository assetRepository;
    private final FmWorkOrderRepository workOrderRepository;
    private final FmProfileRepository profileRepository;
    private final FmNumberGenerator numberGenerator;
    private final FmAccessPolicy accessPolicy;
    private final FmAuditService auditService;

    @Transactional(readOnly = true)
    public Page<FmQuoteResponse> list(TenantId tenantId, String status, UUID clientId, Pageable pageable) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        if (scope.assignedOnly()) return Page.empty(pageable);
        if (clientId != null) scope.requireClient(clientId);
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        Page<FmQuote> page = quoteRepository.findAllInScope(tenantId, s, clientId, scope.unrestricted(), scope.clientIds(), pageable);
        Map<UUID, List<FmQuoteLine>> lines = page.isEmpty() ? Map.of()
                : lineRepository.findForQuotes(tenantId, page.map(FmQuote::getId).getContent()).stream().collect(Collectors.groupingBy(FmQuoteLine::getQuoteId));
        LocalDate today = FmTime.today();
        return page.map(q -> toResponse(q, lines.getOrDefault(q.getId(), List.of()), today));
    }

    @Transactional(readOnly = true)
    public FmQuoteResponse get(TenantId tenantId, UUID id) {
        FmQuote q = find(tenantId, id);
        return toResponse(q, lineRepository.findForQuote(tenantId, id), FmTime.today());
    }

    @Transactional
    public FmQuoteResponse create(TenantId tenantId, FmQuoteRequest req) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        scope.requireClient(req.clientId());
        clientRepository.findActiveById(tenantId, req.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("FmClient", req.clientId().toString()));
        siteRepository.findActiveByIdForClient(tenantId, req.clientId(), req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("FmSite", req.siteId().toString()));
        if (req.assetId() != null) {
            FmAsset asset = assetRepository.findActiveById(tenantId, req.assetId())
                    .orElseThrow(() -> new ResourceNotFoundException("FmAsset", req.assetId().toString()));
            if (!asset.getSiteId().equals(req.siteId())) throw new IllegalArgumentException("The selected asset does not belong to the selected site");
        }
        if (req.workOrderId() != null) {
            FmWorkOrder wo = workOrderRepository.findActiveById(tenantId, req.workOrderId())
                    .filter(w -> w.getClientId().equals(req.clientId()))
                    .orElseThrow(() -> new ResourceNotFoundException("FmWorkOrder", req.workOrderId().toString()));
            if (TERMINAL_WORK_ORDER.contains(wo.getStatus()))
                throw new IllegalArgumentException("Work order " + wo.getWorkOrderNumber() + " is " + wo.getStatus() + " and cannot be quoted");
        }
        FmQuoteRules.requireValidity(req.validUntil(), FmTime.today());

        FmQuote q = FmQuote.create(tenantId, numberGenerator.nextQuoteNumber(tenantId), req.clientId(), req.siteId(), req.assetId(),
                req.workOrderId(), req.title(), req.description(), req.validUntil(), req.internalNote(), TenantContext.getCurrentUserId());
        List<FmQuoteLine> lines = buildLines(tenantId, q.getId(), req.lines());
        applyTotals(tenantId, q, lines);
        quoteRepository.save(q);
        lineRepository.saveAll(lines);
        auditService.record(tenantId, "QUOTE_CREATED", "QUOTE", q.getId(),
                q.getQuoteNumber() + " client=" + q.getClientId() + " total=" + q.getTotal() + " validUntil=" + q.getValidUntil());
        return toResponse(q, lines, FmTime.today());
    }

    @Transactional
    public FmQuoteResponse update(TenantId tenantId, UUID id, FmQuoteRequest req) {
        FmQuote q = find(tenantId, id);
        FmQuoteRules.requireValidity(req.validUntil(), FmTime.today());
        try {
            q.edit(req.title(), req.description(), req.validUntil(), req.internalNote());
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "QUOTE_NOT_DRAFT");
        }
        lineRepository.deleteForQuote(tenantId, id);
        List<FmQuoteLine> lines = buildLines(tenantId, id, req.lines());
        applyTotals(tenantId, q, lines);
        quoteRepository.save(q);
        lineRepository.saveAll(lines);
        auditService.record(tenantId, "QUOTE_UPDATED", "QUOTE", id, q.getQuoteNumber() + " total=" + q.getTotal());
        return toResponse(q, lines, FmTime.today());
    }

    /** Freezes the quote and makes it visible to the client. Totals are recomputed at the VAT rate in force now. */
    @Transactional
    public FmQuoteResponse send(TenantId tenantId, UUID id) {
        FmQuote q = find(tenantId, id);
        List<FmQuoteLine> lines = lineRepository.findForQuote(tenantId, id);
        if (lines.isEmpty()) throw new IllegalArgumentException("Add at least one line before sending");
        LocalDate today = FmTime.today();
        FmQuoteRules.requireValidity(q.getValidUntil(), today);
        applyTotals(tenantId, q, lines);
        try {
            q.send(TenantContext.getCurrentUserId(), today);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "QUOTE_NOT_DRAFT");
        }
        quoteRepository.save(q);
        auditService.record(tenantId, "QUOTE_SENT", "QUOTE", id, q.getQuoteNumber() + " total=" + q.getTotal() + " validUntil=" + q.getValidUntil());
        return toResponse(q, lines, today);
    }

    @Transactional
    public FmQuoteResponse withdraw(TenantId tenantId, UUID id, String reason) {
        FmQuote q = find(tenantId, id);
        if (q.isExpiredOn(FmTime.today()))
            throw new HandyFlowException("This quote has already expired", HttpStatus.CONFLICT, "QUOTE_EXPIRED");
        try {
            q.withdraw(reason);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "QUOTE_NOT_OPEN");
        }
        quoteRepository.save(q);
        auditService.record(tenantId, "QUOTE_WITHDRAWN", "QUOTE", id, q.getQuoteNumber() + " reason=" + q.getDecisionNote());
        return toResponse(q, lineRepository.findForQuote(tenantId, id), FmTime.today());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<FmQuoteLine> buildLines(TenantId tenantId, UUID quoteId, List<FmQuoteRequest.Line> in) {
        List<FmQuoteLine> out = new ArrayList<>();
        int n = 1;
        for (FmQuoteRequest.Line l : in) out.add(FmQuoteLine.create(tenantId, quoteId, n++, l.description(), l.quantity(), l.unitPrice()));
        return out;
    }

    private void applyTotals(TenantId tenantId, FmQuote q, List<FmQuoteLine> lines) {
        FmBillingSettings settings = FmBillingSettings.resolve(profileRepository.findByTenant(tenantId));
        FmQuoteRules.Totals t = FmQuoteRules.totals(lines, settings);
        q.setAmounts(t.subtotal(), t.vatRate(), t.vatAmount(), t.total());
    }

    private FmQuote find(TenantId tenantId, UUID id) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        FmQuote q = quoteRepository.findByIdForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("FmQuote", id.toString()));
        if (!scope.canSeeClient(q.getClientId())) throw new ResourceNotFoundException("FmQuote", id.toString());
        return q;
    }

    static FmQuoteResponse toResponse(FmQuote q, List<FmQuoteLine> lines, LocalDate today) {
        return new FmQuoteResponse(q.getId(), q.getQuoteNumber(), q.getClientId(), q.getSiteId(), q.getAssetId(), q.getWorkOrderId(),
                q.getTitle(), q.getDescription(), FmQuoteRules.effectiveStatus(q, today), q.getValidUntil(), q.getSubtotal(), q.getVatRate(),
                q.getVatAmount(), q.getTotal(), q.getInternalNote(), q.getSentAt(), q.getDecidedAt(), q.getDecisionNote(), q.getCreatedAt(),
                lines.stream().map(l -> new FmQuoteResponse.Line(l.getLineNo(), l.getDescription(), l.getQuantity(), l.getUnitPrice(), l.getLineTotal())).toList());
    }
}
