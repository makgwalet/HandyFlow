package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.debtcollection.application.LegalMatterGateway;
import za.co.handyflow.platform.debtcollection.application.LegalMatterGateway.HandoverTransfer;
import za.co.handyflow.platform.debtcollection.application.LegalMatterGateway.MatterRef;
import za.co.handyflow.platform.debtcollection.application.internal.HandoverReadinessChecker.Facts;
import za.co.handyflow.platform.debtcollection.application.internal.HandoverReadinessChecker.Finding;
import za.co.handyflow.platform.debtcollection.application.internal.HandoverReadinessChecker.NoticeFact;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.Communication;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationDirection;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationPurpose;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CreditAgreementType;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.model.HandoverStatus;
import za.co.handyflow.platform.debtcollection.domain.model.LegalEvent;
import za.co.handyflow.platform.debtcollection.domain.model.LegalHandover;
import za.co.handyflow.platform.debtcollection.domain.model.LegalNotice;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeType;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlanStatus;
import za.co.handyflow.platform.debtcollection.domain.repository.CommunicationRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.LegalEventRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.LegalHandoverRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.LegalNoticeRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.PaymentPlanRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.PromiseToPayRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Controlled legal handover: draft → submit (checks + frozen package) → approve (four-eyes, checks re-run) →
 * transfer (through LegalMatterGateway) → status sync back from Legal Practice.
 * <p>
 * "Done" results: submit/approve/transfer return an ActionResult; when a BLOCKING finding stops the step, done is
 * false and the findings explain why — an expected business outcome, not an exception. Illegal state changes
 * (wrong status, approving your own handover) do throw.
 * <p>
 * TRANSFER is not one transaction, on purpose: the gateway call happens outside any transaction, then a short
 * transaction records TRANSFERRED, links the matter and moves the case to HANDED_TO_LEGAL. If our bookkeeping
 * fails after the matter was created, a retry calls the gateway again — which the gateway contract requires to be
 * idempotent on handoverId — and converges on the same matter. Case status changes only after a successful transfer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegalHandoverService {

    public record ActionResult(LegalHandover handover, List<Finding> findings, boolean done) {}

    private final LegalHandoverRepository repository;
    private final LegalEventRepository eventRepository;
    private final LegalNoticeRepository noticeRepository;
    private final CommunicationRepository communicationRepository;
    private final PromiseToPayRepository ptpRepository;
    private final PaymentPlanRepository planRepository;
    private final DebtCollectionCaseService caseService;
    private final LegalHandoverPackageBuilder packageBuilder;
    private final HandoverReadinessChecker checker;
    private final ObjectProvider<LegalMatterGateway> gateway;
    private final TransactionTemplate tx;

    // ── drafting ─────────────────────────────────────────────────────────────

    @Transactional
    public LegalHandover create(TenantId tenantId, UUID caseId, CreditAgreementType agreementType, String authorityNote,
                                Set<UUID> evidenceIds, String notes, UUID actorId, String actorName) {
        caseService.get(tenantId, caseId);
        if (!repository.findOpenByCaseId(tenantId, caseId).isEmpty()) {
            throw new IllegalStateException("This case already has an open legal handover — finish or cancel it first");
        }
        LegalHandover h = repository.save(LegalHandover.draft(tenantId, caseId, agreementType, authorityNote, evidenceIds,
                notes, actorId, actorName));
        audit(h, "CREATED", "agreement type " + h.getAgreementType(), actorId, actorName);
        return h;
    }

    @Transactional
    public LegalHandover updateDraft(TenantId tenantId, UUID id, CreditAgreementType agreementType, String authorityNote,
                                     Set<UUID> evidenceIds, String notes, UUID actorId, String actorName) {
        LegalHandover h = find(tenantId, id);
        CreditAgreementType before = h.getAgreementType();
        h.updateDraft(agreementType, authorityNote, evidenceIds, notes);
        audit(h, "DRAFT_UPDATED", before != h.getAgreementType()
                ? "agreement type " + before + " → " + h.getAgreementType() + " (prescription review reset)" : "draft updated",
                actorId, actorName);
        return repository.save(h);
    }

    @Transactional
    public LegalHandover recordPrescriptionReview(TenantId tenantId, UUID id, String note, UUID actorId, String actorName) {
        LegalHandover h = find(tenantId, id);
        h.recordPrescriptionReview(note, actorId);
        audit(h, "PRESCRIPTION_REVIEWED", note, actorId, actorName);
        return repository.save(h);
    }

    // ── review workflow ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Finding> readiness(TenantId tenantId, UUID id) {
        LegalHandover h = find(tenantId, id);
        return checker.check(facts(tenantId, caseService.get(tenantId, h.getCaseId()), h));
    }

    @Transactional
    public ActionResult submit(TenantId tenantId, UUID id, UUID actorId, String actorName) {
        LegalHandover h = find(tenantId, id);
        requireStatus(h, HandoverStatus.DRAFT, "submit");
        DebtCollectionCase c = caseService.get(tenantId, h.getCaseId());
        List<Finding> findings = checker.check(facts(tenantId, c, h));
        if (HandoverReadinessChecker.hasBlocking(findings)) return new ActionResult(h, findings, false);
        var built = packageBuilder.build(tenantId, h, c, h.getPackageVersion() + 1, actorName);
        h.submit(built.json(), built.hash());
        audit(h, "SUBMITTED", "package v" + h.getPackageVersion() + " sha256 " + built.hash(), actorId, actorName);
        return new ActionResult(repository.save(h), findings, true);
    }

    @Transactional
    public ActionResult approve(TenantId tenantId, UUID id, UUID actorId, String actorName) {
        LegalHandover h = find(tenantId, id);
        requireStatus(h, HandoverStatus.IN_REVIEW, "approve");
        List<Finding> findings = checker.check(facts(tenantId, caseService.get(tenantId, h.getCaseId()), h));
        if (HandoverReadinessChecker.hasBlocking(findings)) return new ActionResult(h, findings, false);
        h.approve(actorId, actorName); // enforces IN_REVIEW and approver != preparer
        audit(h, "APPROVED", "package v" + h.getPackageVersion() + " sha256 " + h.getPackageHash(), actorId, actorName);
        return new ActionResult(repository.save(h), findings, true);
    }

    @Transactional
    public LegalHandover returnToDraft(TenantId tenantId, UUID id, String reason, UUID actorId, String actorName) {
        LegalHandover h = find(tenantId, id);
        h.returnToDraft(reason);
        audit(h, "RETURNED", "package v" + h.getPackageVersion() + " returned: " + reason, actorId, actorName);
        return repository.save(h);
    }

    @Transactional
    public LegalHandover cancel(TenantId tenantId, UUID id, String reason, UUID actorId, String actorName) {
        LegalHandover h = find(tenantId, id);
        h.cancel(reason);
        audit(h, "CANCELLED", reason, actorId, actorName);
        return repository.save(h);
    }

    // ── transfer & sync ──────────────────────────────────────────────────────

    /** Not @Transactional — see class Javadoc. */
    public ActionResult transfer(TenantId tenantId, UUID id, UUID actorId, String actorName) {
        LegalHandover h = find(tenantId, id);
        requireStatus(h, HandoverStatus.APPROVED, "transfer");
        DebtCollectionCase c = caseService.get(tenantId, h.getCaseId());
        List<Finding> findings = checker.check(facts(tenantId, c, h));
        if (HandoverReadinessChecker.hasBlocking(findings)) return new ActionResult(h, findings, false);
        LegalMatterGateway gw = gateway.getIfAvailable();
        if (gw == null) {
            throw new IllegalStateException("No Legal Practice gateway is configured: the legal module must provide a "
                    + "LegalMatterGateway bean before handovers can be transferred");
        }
        MatterRef ref = gw.createMatterFromHandover(new HandoverTransfer(tenantId, h.getId(), c.getId(), c.getCaseNumber(),
                c.getDebtorName(), c.getCustomerId(), c.getTotalOutstanding(), h.getAgreementType().name(),
                h.getPackageJson(), h.getPackageHash(), c.getLinkedContractId(), h.getEvidenceIds(), actorId));
        if (ref == null || ref.matterId() == null) {
            throw new IllegalStateException("Legal Practice did not return a matter id; the handover remains APPROVED");
        }
        LegalHandover done = tx.execute(s -> {
            LegalHandover fresh = find(tenantId, id);
            fresh.markTransferred(ref.matterId(), ref.matterNumber(), actorId);
            audit(fresh, "TRANSFERRED", "legal matter " + ref.matterNumber() + " (" + ref.matterId() + "), package sha256 "
                    + fresh.getPackageHash(), actorId, actorName);
            LegalHandover saved = repository.save(fresh);
            caseService.advanceStatus(tenantId, fresh.getCaseId(), CaseStatus.HANDED_TO_LEGAL);
            return saved;
        });
        return new ActionResult(done, findings, true);
    }

    /** Entry point for Legal Practice to report progress (by handover). */
    @Transactional
    public LegalHandover syncLegalStatus(TenantId tenantId, UUID id, String legalStatus, String detail, UUID actorId,
                                         String actorName) {
        LegalHandover h = find(tenantId, id);
        String before = h.getLegalStatus();
        h.syncLegalStatus(legalStatus);
        audit(h, "LEGAL_STATUS", (before != null ? before : "none") + " → " + h.getLegalStatus()
                + (detail != null && !detail.isBlank() ? " — " + detail : ""), actorId, actorName);
        return repository.save(h);
    }

    /** Same, addressed by the legal matter id the adapter knows. */
    @Transactional
    public LegalHandover syncLegalStatusByMatter(TenantId tenantId, UUID matterId, String legalStatus, String detail) {
        LegalHandover h = repository.findByLegalMatterId(tenantId, matterId)
                .orElseThrow(() -> new ResourceNotFoundException("LegalHandover (matter)", matterId.toString()));
        return syncLegalStatus(tenantId, h.getId(), legalStatus, detail, null, "legal-practice");
    }

    // ── queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LegalHandover get(TenantId tenantId, UUID id) {
        return find(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<LegalHandover> listForCase(TenantId tenantId, UUID caseId) {
        caseService.get(tenantId, caseId);
        return repository.findByCaseId(tenantId, caseId);
    }

    @Transactional(readOnly = true)
    public List<LegalEvent> history(TenantId tenantId, UUID id) {
        find(tenantId, id);
        return eventRepository.findBySubjectId(tenantId, id);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private Facts facts(TenantId tenantId, DebtCollectionCase c, LegalHandover h) {
        var inv = packageBuilder.invoices(tenantId, c);
        List<LegalNotice> notices = noticeRepository.findByCaseId(tenantId, c.getId());
        boolean demand = notices.stream().anyMatch(n -> n.getNoticeType() == NoticeType.LETTER_OF_DEMAND)
                || communicationRepository.findByCaseId(tenantId, c.getId()).stream().anyMatch(this::isSentDemand);
        boolean activePlan = planRepository.findByCaseId(tenantId, c.getId()).stream()
                .anyMatch(p -> p.getStatus() == PaymentPlanStatus.ACTIVE);
        boolean authority = (h.getCreditorAuthorityNote() != null && !h.getCreditorAuthorityNote().isBlank())
                || !h.getEvidenceIds().isEmpty();
        return new Facts(c.getStatus(), inv.totalOutstanding(), h.getAgreementType(), h.isPrescriptionReviewed(), authority,
                !ptpRepository.findOpenByCaseId(tenantId, c.getId()).isEmpty(), activePlan, demand,
                notices.stream().map(n -> new NoticeFact(n.getNoticeType(), n.getReviewStatus(), n.getDeliveryStatus())).toList());
    }

    private boolean isSentDemand(Communication x) {
        return x.getPurpose() == CommunicationPurpose.DEMAND && x.getDirection() == CommunicationDirection.OUTBOUND
                && (x.getStatus() == CommunicationStatus.SENT || x.getStatus() == CommunicationStatus.DELIVERED);
    }

    private static void requireStatus(LegalHandover h, HandoverStatus expected, String action) {
        if (h.getStatus() != expected) {
            throw new IllegalStateException("Cannot " + action + " a handover that is " + h.getStatus() + " (needs " + expected + ")");
        }
    }

    private LegalHandover find(TenantId tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("LegalHandover", id.toString()));
    }

    private void audit(LegalHandover h, String type, String detail, UUID actorId, String actorName) {
        eventRepository.save(LegalEvent.of(h.getTenantId(), "HANDOVER", h.getId(), h.getCaseId(), type, detail, actorId, actorName));
    }
}
