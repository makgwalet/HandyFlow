package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmQuote;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmQuoteLine;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmQuoteLineRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmQuoteRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalQuoteResponse;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The client's side of quotes. A portal user sees only quotes that were sent to their own client (drafts never leave the
 * staff side; anything else looks like not found) and can accept or decline one that is still open and within its
 * validity. Accepting is the client's authorisation for the work: when the quote was not tied to an existing request, a
 * work order is created for the quoted site and asset. The accepted amount is a record of what was agreed; it does not
 * change how the work is later invoiced.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmPortalQuoteService {

    private final FmPortalDataService portalDataService;
    private final FmQuoteRepository quoteRepository;
    private final FmQuoteLineRepository lineRepository;
    private final FmWorkOrderRepository workOrderRepository;
    private final FmNumberGenerator numberGenerator;
    private final FmContractTermsService contractTermsService;
    private final FmAuditService auditService;
    private final FmAlerts alerts;

    @Transactional(readOnly = true)
    public List<FmPortalQuoteResponse> list(UUID portalUserId, UUID selectedClientId) {
        FmPortalDataService.Scope scope = portalDataService.resolveScope(portalUserId, selectedClientId);
        LocalDate today = FmTime.today();
        return quoteRepository.findVisibleForClient(scope.tenantId(), scope.clientId()).stream()
                .map(q -> toResponse(q, lineRepository.findForQuote(scope.tenantId(), q.getId()), today)).toList();
    }

    @Transactional(readOnly = true)
    public FmPortalQuoteResponse get(UUID portalUserId, UUID selectedClientId, UUID id) {
        FmPortalDataService.Scope scope = portalDataService.resolveScope(portalUserId, selectedClientId);
        FmQuote q = findVisible(scope, id);
        return toResponse(q, lineRepository.findForQuote(scope.tenantId(), id), FmTime.today());
    }

    @Transactional
    public FmPortalQuoteResponse accept(UUID portalUserId, UUID selectedClientId, UUID id, String note) {
        FmPortalDataService.Scope scope = portalDataService.resolveScope(portalUserId, selectedClientId);
        TenantId tenantId = scope.tenantId();
        FmQuote q = findVisible(scope, id);
        LocalDate today = FmTime.today();
        decide(q, today, () -> q.accept(portalUserId, note, today));

        FmWorkOrder created = null;
        if (q.getWorkOrderId() == null) {
            String number = numberGenerator.nextWorkOrderNumber(tenantId);
            String description = ("Approved quote " + q.getQuoteNumber() + ": " + q.getTitle());
            if (description.length() > 250) description = description.substring(0, 247) + "...";
            created = FmWorkOrder.create(tenantId, number, q.getClientId(), q.getSiteId(), q.getAssetId(), null, "CORRECTIVE", "NORMAL",
                    description, "Client portal (quote " + q.getQuoteNumber() + ")", null);
            contractTermsService.applyTerms(tenantId, created);
            workOrderRepository.save(created);
            q.linkWorkOrder(created.getId());
        }
        quoteRepository.save(q);

        auditService.recordPortal(tenantId, portalUserId, "QUOTE_ACCEPTED", "QUOTE", q.getId(),
                q.getQuoteNumber() + " total=" + q.getTotal() + (created != null ? " workOrder=" + created.getWorkOrderNumber() : " workOrder=" + q.getWorkOrderId()));
        alerts.toAdmins(tenantId, NotificationType.FM_QUOTE_ACCEPTED, "Quote accepted: " + q.getQuoteNumber(),
                q.getTitle() + " (R" + q.getTotal() + ") was accepted by the client."
                        + (created != null ? " Work order " + created.getWorkOrderNumber() + " was created." : " It is ready to be scheduled."),
                created != null ? "/facilitiesmanagement/work-orders/" + created.getId() : "/facilitiesmanagement/quotes/" + q.getId(), q.getId().toString());
        log.info("FM quote accepted number={} client={} tenant={}", q.getQuoteNumber(), q.getClientId(), tenantId);
        return toResponse(q, lineRepository.findForQuote(tenantId, id), today);
    }

    @Transactional
    public FmPortalQuoteResponse decline(UUID portalUserId, UUID selectedClientId, UUID id, String reason) {
        FmPortalDataService.Scope scope = portalDataService.resolveScope(portalUserId, selectedClientId);
        FmQuote q = findVisible(scope, id);
        LocalDate today = FmTime.today();
        decide(q, today, () -> q.decline(portalUserId, reason, today));
        quoteRepository.save(q);
        auditService.recordPortal(scope.tenantId(), portalUserId, "QUOTE_DECLINED", "QUOTE", q.getId(), q.getQuoteNumber() + " reason=" + q.getDecisionNote());
        alerts.toAdmins(scope.tenantId(), NotificationType.FM_QUOTE_DECLINED, "Quote declined: " + q.getQuoteNumber(),
                q.getTitle() + " was declined by the client: " + q.getDecisionNote(), "/facilitiesmanagement/quotes/" + q.getId(), q.getId().toString());
        return toResponse(q, lineRepository.findForQuote(scope.tenantId(), id), today);
    }

    /** Maps the entity's state errors to stable 409s; a missing decline reason stays a 400. */
    private void decide(FmQuote q, LocalDate today, Runnable action) {
        boolean expired = q.isExpiredOn(today);
        try {
            action.run();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, expired ? "QUOTE_EXPIRED" : "QUOTE_NOT_OPEN");
        }
    }

    private FmQuote findVisible(FmPortalDataService.Scope scope, UUID id) {
        return quoteRepository.findByIdForTenant(scope.tenantId(), id)
                .filter(q -> q.getClientId().equals(scope.clientId()) && !"DRAFT".equals(q.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("FmQuote", id.toString()));
    }

    static FmPortalQuoteResponse toResponse(FmQuote q, List<FmQuoteLine> lines, LocalDate today) {
        return new FmPortalQuoteResponse(q.getId(), q.getQuoteNumber(), q.getSiteId(), q.getTitle(), q.getDescription(),
                FmQuoteRules.effectiveStatus(q, today), q.isOpenForDecision(today), q.getValidUntil(), q.getSubtotal(), q.getVatRate(),
                q.getVatAmount(), q.getTotal(), q.getSentAt(), q.getDecidedAt(), q.getDecisionNote(),
                lines.stream().map(l -> new FmPortalQuoteResponse.Line(l.getLineNo(), l.getDescription(), l.getQuantity(), l.getUnitPrice(), l.getLineTotal())).toList());
    }
}
