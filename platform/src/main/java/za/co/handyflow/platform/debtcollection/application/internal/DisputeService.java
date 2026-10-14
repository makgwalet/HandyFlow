package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.model.Dispute;
import za.co.handyflow.platform.debtcollection.domain.repository.DebtCollectionCaseRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.DisputeRepository;
import za.co.handyflow.platform.debtcollection.dto.AssignDisputeRequest;
import za.co.handyflow.platform.debtcollection.dto.DisputeResponse;
import za.co.handyflow.platform.debtcollection.dto.OpenDisputeRequest;
import za.co.handyflow.platform.debtcollection.dto.ResolveDisputeRequest;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.OutstandingInvoiceSummary;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Dispute records (finance F16). Opening a dispute puts the case in DISPUTED, which already stops collection
 * messages, fees and handover to legal; resolving the last open dispute puts the case back. Every step is
 * written to the case audit trail in the same transaction.
 */
@Service
@RequiredArgsConstructor
public class DisputeService {

    private final DisputeRepository disputes;
    private final DebtCollectionCaseRepository cases;
    private final InvoicingFacade invoicingFacade;
    private final CaseAuditService audit;
    private final LegalProperties legalProperties;

    @Transactional(readOnly = true)
    public List<DisputeResponse> forCase(TenantId tenantId, UUID caseId) {
        DebtCollectionCase c = findCase(tenantId, caseId);
        LocalDate today = LocalDate.now();
        return disputes.findByCaseId(tenantId, caseId).stream()
                .map(d -> DisputeResponse.of(d, c.getCaseNumber(), c.getDebtorName(), today)).toList();
    }

    /** Every unresolved dispute in the business, soonest response date first. */
    @Transactional(readOnly = true)
    public List<DisputeResponse> unresolved(TenantId tenantId) {
        LocalDate today = LocalDate.now();
        return disputes.findUnresolved(tenantId).stream().map(d -> {
            DebtCollectionCase c = cases.findActiveById(tenantId, d.getCaseId()).orElse(null);
            return DisputeResponse.of(d, c == null ? null : c.getCaseNumber(), c == null ? null : c.getDebtorName(), today);
        }).toList();
    }

    @Transactional
    public DisputeResponse open(TenantId tenantId, UUID caseId, OpenDisputeRequest req) {
        DebtCollectionCase c = findCase(tenantId, caseId);
        BigDecimal invoiceOwes = null;
        if (req.invoiceId() != null) {
            for (OutstandingInvoiceSummary inv : invoicingFacade.findOutstandingInvoices(tenantId)) {
                if (inv.id().equals(req.invoiceId())) {
                    invoiceOwes = inv.total().subtract(inv.settled());
                    break;
                }
            }
        }
        boolean onCase = req.invoiceId() != null && c.getLinkedInvoiceIds().contains(req.invoiceId());
        String problem = DisputeRules.openProblem(c.getStatus(), req.reasonType(), req.description(), onCase,
                req.amountDisputed(), invoiceOwes, req.invoiceId() != null);
        if (problem != null) throw new IllegalArgumentException(problem);

        LocalDate today = LocalDate.now();
        UUID me = TenantContext.getCurrentUserId();
        UUID ownerId = req.ownerUserId() != null ? req.ownerUserId() : c.getAssignedToUserId() != null ? c.getAssignedToUserId() : me;
        String ownerName = req.ownerUserId() != null ? req.ownerUserName()
                : c.getAssignedToUserId() != null ? c.getAssignedToUserName() : TenantContext.getCurrentUserName();
        CaseStatus before = c.getStatus();
        Dispute d = Dispute.open(tenantId, caseId, req.invoiceId(), req.reasonType(), req.description(), req.amountDisputed(),
                today, DisputeRules.responseDue(today, new HashSet<>(legalProperties.getPublicHolidays())),
                ownerId, ownerName, DisputeRules.statusToRemember(before), me);
        d = disputes.save(d);

        CaseStatus after = DisputeRules.statusAfterOpen(before);
        if (after != before) {
            c.advanceStatus(after);
            cases.save(c);
        }
        audit.record(tenantId, caseId, "DISPUTE_OPENED", d.getReasonType() + (req.invoiceId() == null ? "" : " on invoice " + req.invoiceId())
                + (after != before ? "; case " + before + " → " + after : "; case status kept at " + before));
        return DisputeResponse.of(d, c.getCaseNumber(), c.getDebtorName(), today);
    }

    @Transactional
    public DisputeResponse takeOn(TenantId tenantId, UUID disputeId) {
        Dispute d = find(tenantId, disputeId);
        String problem = DisputeRules.takeOnProblem(d.getStatus());
        if (problem != null) throw new IllegalStateException(problem);
        d.takeOn(TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        audit.record(tenantId, d.getCaseId(), "DISPUTE_IN_REVIEW", "taken into review by " + TenantContext.getCurrentUserName());
        return view(tenantId, disputes.save(d));
    }

    @Transactional
    public DisputeResponse assign(TenantId tenantId, UUID disputeId, AssignDisputeRequest req) {
        Dispute d = find(tenantId, disputeId);
        if ("RESOLVED".equals(d.getStatus())) throw new IllegalStateException("This dispute is already resolved.");
        if (req.userId() == null) throw new IllegalArgumentException("Choose who owns the dispute.");
        d.reassign(req.userId(), req.userName());
        audit.record(tenantId, d.getCaseId(), "DISPUTE_ASSIGNED", "owner is now " + req.userName());
        return view(tenantId, disputes.save(d));
    }

    @Transactional
    public DisputeResponse resolve(TenantId tenantId, UUID disputeId, ResolveDisputeRequest req) {
        Dispute d = find(tenantId, disputeId);
        String problem = DisputeRules.resolveProblem(d.getStatus(), req.resolution(), req.note(), req.amountConceded(), d.getAmountDisputed());
        if (problem != null) throw new IllegalArgumentException(problem);
        d.resolve(req.resolution(), req.note(), req.amountConceded(), req.creditNoteRef(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        disputes.save(d);

        DebtCollectionCase c = findCase(tenantId, d.getCaseId());
        boolean others = disputes.countOtherUnresolved(tenantId, d.getCaseId(), d.getId()) > 0;
        CaseStatus now = c.getStatus();
        CaseStatus next = DisputeRules.statusAfterResolve(now, d.getCaseStatusBefore(), others);
        if (next != now) {
            c.advanceStatus(next);
            cases.save(c);
        }
        audit.record(tenantId, d.getCaseId(), "DISPUTE_RESOLVED", d.getResolution()
                + (d.getAmountConceded() == null ? "" : ", conceded " + d.getAmountConceded())
                + (next != now ? "; case " + now + " → " + next : ""));
        return DisputeResponse.of(d, c.getCaseNumber(), c.getDebtorName(), LocalDate.now());
    }

    private DisputeResponse view(TenantId tenantId, Dispute d) {
        DebtCollectionCase c = cases.findActiveById(tenantId, d.getCaseId()).orElse(null);
        return DisputeResponse.of(d, c == null ? null : c.getCaseNumber(), c == null ? null : c.getDebtorName(), LocalDate.now());
    }

    private Dispute find(TenantId tenantId, UUID id) {
        return disputes.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Dispute", id.toString()));
    }

    private DebtCollectionCase findCase(TenantId tenantId, UUID id) {
        return cases.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("DebtCollectionCase", id.toString()));
    }
}
