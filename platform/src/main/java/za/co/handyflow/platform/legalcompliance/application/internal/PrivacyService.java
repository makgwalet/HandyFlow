package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.EvidenceRef;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.FindingNoteRequest;
import za.co.handyflow.platform.legalcompliance.dto.InfoGovDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Privacy and access-to-information work: privacy impact assessments, PAIA requests and regulator filings. The existing POPIA processing register and
 * DSAR tracker are untouched; a PIA may point at a processing activity.
 *
 * Nothing here files with, or gets approved by, a regulator. A PIA approval is the organisation's own sign-off (reviewer is never the assessor); a filing
 * is a record that the organisation prepared and submitted something, with proof files snapshotted at the moment of submission.
 */
@Service
@RequiredArgsConstructor
public class PrivacyService {

    static final String SOURCE_MODULE = "legalcompliance";
    static final String PIA = "PIA", PAIA = "PAIA_REQUEST", FILING = "FILING";

    private final PiaRepository pias;
    private final PaiaRequestRepository paia;
    private final FilingRepository filings;
    private final PopiaProcessingActivityRepository activities;
    private final EvidenceFacade evidence;
    private final RegulatoryAuditService trail;

    // ═══ PIA ═══

    @Transactional
    public PiaResponse createPia(TenantId t, PiaRequest r, UUID uid, String uname) {
        checkActivity(t, r.popiaActivityId());
        Pia p = guarded(() -> Pia.create(t, String.format("PIA-%05d", pias.nextRef()), r.title(), r.popiaActivityId(), r.description(), r.necessity(), r.risks(), r.mitigations(), r.residualRisk(), uid, uname));
        pias.save(p);
        trail.record(t.getValue(), PIA, p.getId(), "CREATED", p.getReference() + " " + p.getTitle(), uid, uname);
        return view(t, p);
    }

    @Transactional
    public PiaResponse editPia(TenantId t, UUID id, PiaRequest r, UUID uid, String uname) {
        Pia p = loadPia(t, id);
        checkActivity(t, r.popiaActivityId());
        guarded(() -> { p.edit(r.title(), r.popiaActivityId(), r.description(), r.necessity(), r.risks(), r.mitigations(), r.residualRisk(), uid, uname); return null; });
        pias.save(p);
        trail.record(t.getValue(), PIA, id, "EDITED", "Edited by " + uname, uid, uname);
        return view(t, p);
    }

    @Transactional
    public PiaResponse submitPia(TenantId t, UUID id, UUID uid, String uname) {
        Pia p = loadPia(t, id);
        guarded(() -> { p.submit(); return null; });
        pias.save(p);
        trail.record(t.getValue(), PIA, id, "SUBMITTED", "Residual risk " + p.getResidualRisk(), uid, uname);
        return view(t, p);
    }

    @Transactional
    public PiaResponse approvePia(TenantId t, UUID id, PiaApproveRequest r, UUID uid, String uname) {
        Pia p = loadPia(t, id);
        sodPia(p, uid);
        guarded(() -> { p.approve(uid, uname, r.note(), r.reviewMonths() == 0 ? 12 : r.reviewMonths()); return null; });
        pias.save(p);
        trail.record(t.getValue(), PIA, id, "APPROVED", "Residual risk " + p.getResidualRisk() + ", next review " + p.getNextReviewDue(), uid, uname);
        return view(t, p);
    }

    @Transactional
    public PiaResponse rejectPia(TenantId t, UUID id, FindingNoteRequest r, UUID uid, String uname) {
        Pia p = loadPia(t, id);
        sodPia(p, uid);
        guarded(() -> { p.reject(uid, uname, r.note()); return null; });
        pias.save(p);
        trail.record(t.getValue(), PIA, id, "REJECTED", r.note().trim(), uid, uname);
        return view(t, p);
    }

    @Transactional(readOnly = true)
    public List<PiaResponse> listPias(TenantId t, PiaStatus status) { return pias.search(t, status).stream().map(p -> view(t, p)).toList(); }

    @Transactional(readOnly = true)
    public PiaSummary piaSummary(TenantId t) {
        List<Pia> all = pias.search(t, null);
        LocalDate today = LocalDate.now();
        return new PiaSummary(count(all, PiaStatus.DRAFT), count(all, PiaStatus.SUBMITTED), count(all, PiaStatus.APPROVED), count(all, PiaStatus.REJECTED),
                all.stream().filter(p -> p.getStatus() == PiaStatus.APPROVED && p.getResidualRisk() == RiskLevel.HIGH).count(),
                all.stream().filter(p -> p.getStatus() == PiaStatus.APPROVED && PrivacyPartyRules.reviewOverdue(p.getNextReviewDue(), today)).count());
    }

    private static long count(List<Pia> all, PiaStatus s) { return all.stream().filter(p -> p.getStatus() == s).count(); }

    private void sodPia(Pia p, UUID uid) {
        if (uid != null && uid.equals(p.getAssessorId()))
            throw new HandyFlowException("Segregation of duties: you wrote this assessment, so you cannot approve or reject it", HttpStatus.FORBIDDEN, "SOD_VIOLATION");
    }

    private void checkActivity(TenantId t, UUID activityId) {
        if (activityId != null) activities.findActiveById(t, activityId).orElseThrow(() -> new ResourceNotFoundException("POPIA processing activity", activityId.toString()));
    }

    private Pia loadPia(TenantId t, UUID id) { return pias.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Privacy impact assessment", id.toString())); }

    private PiaResponse view(TenantId t, Pia p) {
        String activity = p.getPopiaActivityId() == null ? null : activities.findActiveById(t, p.getPopiaActivityId()).map(PopiaProcessingActivity::getActivityName).orElse(null);
        return new PiaResponse(p.getId(), p.getReference(), p.getTitle(), p.getPopiaActivityId(), activity, p.getDescription(), p.getNecessity(), p.getRisks(), p.getMitigations(), p.getResidualRisk(),
                p.getStatus(), p.getAssessorName(), p.getSubmittedAt(), p.getReviewerName(), p.getReviewedAt(), p.getReviewNote(), p.getNextReviewDue(),
                p.getStatus() == PiaStatus.APPROVED && PrivacyPartyRules.reviewOverdue(p.getNextReviewDue(), LocalDate.now()));
    }

    // ═══ PAIA ═══

    @Transactional
    public PaiaResponse receivePaia(TenantId t, PaiaRequestBody r, UUID uid, String uname) {
        PaiaRequest p = guarded(() -> PaiaRequest.receive(t, String.format("PAIA-%05d", paia.nextRef()), r.requesterName(), r.requesterContact(), r.onBehalfOf(), r.recordDescription(), r.receivedDate(),
                r.receivedDate() == null ? null : PrivacyPartyRules.paiaDueDate(r.receivedDate()), r.assignedTo(), uid));
        paia.save(p);
        trail.record(t.getValue(), PAIA, p.getId(), "RECEIVED", p.getReference() + " from " + p.getRequesterName() + ", due " + p.getDueDate(), uid, uname);
        return view(p);
    }

    @Transactional
    public PaiaResponse updatePaia(TenantId t, UUID id, PaiaRequestBody r, UUID uid, String uname) {
        PaiaRequest p = loadPaia(t, id);
        if (!r.receivedDate().equals(p.getReceivedDate())) throw new HandyFlowException("The received date cannot be changed once the request is logged; it sets the due date", HttpStatus.BAD_REQUEST, "VALIDATION");
        guarded(() -> { p.update(r.requesterName(), r.requesterContact(), r.onBehalfOf(), r.recordDescription(), r.receivedDate(), r.assignedTo()); return null; });
        paia.save(p);
        trail.record(t.getValue(), PAIA, id, "UPDATED", "Details changed", uid, uname);
        return view(p);
    }

    @Transactional
    public PaiaResponse extendPaia(TenantId t, UUID id, PaiaExtendRequest r, UUID uid, String uname) {
        PaiaRequest p = loadPaia(t, id);
        guarded(() -> { p.extend(r.until(), r.reason(), PrivacyPartyRules.paiaMaxExtendedDate(p.getDueDate())); return null; });
        paia.save(p);
        trail.record(t.getValue(), PAIA, id, "EXTENDED", "Until " + r.until() + ": " + r.reason().trim(), uid, uname);
        return view(p);
    }

    @Transactional
    public PaiaResponse feePaia(TenantId t, UUID id, PaiaFeeRequest r, UUID uid, String uname) {
        PaiaRequest p = loadPaia(t, id);
        guarded(() -> { p.recordFee(r.amount(), r.paidOn()); return null; });
        paia.save(p);
        trail.record(t.getValue(), PAIA, id, "FEE_RECORDED", "Amount " + r.amount() + (r.paidOn() == null ? ", unpaid" : ", paid " + r.paidOn()), uid, uname);
        return view(p);
    }

    @Transactional
    public PaiaResponse decidePaia(TenantId t, UUID id, PaiaDecideRequest r, UUID uid, String uname) {
        PaiaRequest p = loadPaia(t, id);
        guarded(() -> { p.decide(r.decision(), r.decisionDate(), r.reason(), r.decidedBy()); return null; });
        paia.save(p);
        trail.record(t.getValue(), PAIA, id, "DECIDED", r.decision() + " on " + r.decisionDate() + ": " + r.reason().trim(), uid, uname);
        return view(p);
    }

    @Transactional
    public PaiaResponse complaintPaia(TenantId t, UUID id, PaiaComplaintRequest r, UUID uid, String uname) {
        PaiaRequest p = loadPaia(t, id);
        guarded(() -> { p.recordComplaint(r.lodgedOn(), r.reference(), r.outcome()); return null; });
        paia.save(p);
        trail.record(t.getValue(), PAIA, id, "COMPLAINT_RECORDED", "Lodged " + r.lodgedOn(), uid, uname);
        return view(p);
    }

    @Transactional
    public PaiaResponse closePaia(TenantId t, UUID id, UUID uid, String uname) {
        PaiaRequest p = loadPaia(t, id);
        guarded(() -> { p.close(); return null; });
        paia.save(p);
        trail.record(t.getValue(), PAIA, id, "CLOSED", "Closed", uid, uname);
        return view(p);
    }

    @Transactional(readOnly = true)
    public List<PaiaResponse> listPaia(TenantId t, PaiaStatus status, boolean includeClosed) { return paia.search(t, status, includeClosed).stream().map(this::view).toList(); }

    @Transactional(readOnly = true)
    public PaiaSummary paiaSummary(TenantId t) {
        List<PaiaRequest> all = paia.search(t, null, true);
        LocalDate today = LocalDate.now();
        List<PaiaRequest> open = all.stream().filter(p -> p.getStatus() == PaiaStatus.RECEIVED).toList();
        return new PaiaSummary(open.size(), open.stream().filter(p -> PrivacyPartyRules.paiaOverdue(eff(p), false, today)).count(), open.stream().filter(p -> PrivacyPartyRules.paiaDueSoon(eff(p), false, today)).count(),
                all.stream().filter(p -> p.getStatus() != PaiaStatus.RECEIVED).count(),
                all.stream().filter(p -> p.getDecision() == PaiaDecision.REFUSED || p.getDecision() == PaiaDecision.PARTIALLY_GRANTED).count());
    }

    private static LocalDate eff(PaiaRequest p) { return PrivacyPartyRules.paiaEffectiveDue(p.getDueDate(), p.getExtendedUntil()); }

    private PaiaRequest loadPaia(TenantId t, UUID id) { return paia.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("PAIA request", id.toString())); }

    private PaiaResponse view(PaiaRequest p) {
        LocalDate today = LocalDate.now();
        boolean decided = p.getStatus() != PaiaStatus.RECEIVED;
        LocalDate eff = eff(p);
        return new PaiaResponse(p.getId(), p.getReference(), p.getRequesterName(), p.getRequesterContact(), p.getOnBehalfOf(), p.getRecordDescription(), p.getReceivedDate(), p.getDueDate(),
                p.getExtendedUntil(), p.getExtensionReason(), eff, p.getStatus(), p.getAssignedToName(), p.getFeeAmount(), p.getFeePaidOn(), p.getDecision(), p.getDecisionDate(), p.getDecisionReason(),
                p.getDecidedByName(), p.getComplaintLodgedOn(), p.getComplaintReference(), p.getComplaintOutcome(), PrivacyPartyRules.paiaOverdue(eff, decided, today),
                PrivacyPartyRules.paiaDueSoon(eff, decided, today), !decided && p.getExtendedUntil() == null, PrivacyPartyRules.paiaMaxExtendedDate(p.getDueDate()));
    }

    // ═══ Filings ═══

    @Transactional
    public FilingResponse planFiling(TenantId t, FilingRequest r, UUID uid, String uname) {
        Filing f = guarded(() -> Filing.plan(t, String.format("FIL-%05d", filings.nextRef()), r.filingType(), r.title(), r.periodLabel(), r.dueDate(), r.ownerName(), r.notes()));
        filings.save(f);
        trail.record(t.getValue(), FILING, f.getId(), "PLANNED", f.getReference() + " " + f.getFilingType() + " due " + f.getDueDate(), uid, uname);
        return view(t, f);
    }

    @Transactional
    public FilingResponse updateFiling(TenantId t, UUID id, FilingRequest r, UUID uid, String uname) {
        Filing f = loadFiling(t, id);
        guarded(() -> { f.update(r.filingType(), r.title(), r.periodLabel(), r.dueDate(), r.ownerName(), r.notes()); return null; });
        filings.save(f);
        trail.record(t.getValue(), FILING, id, "UPDATED", "Details changed", uid, uname);
        return view(t, f);
    }

    @Transactional
    public FilingResponse prepareFiling(TenantId t, UUID id, UUID uid, String uname) {
        Filing f = loadFiling(t, id);
        guarded(() -> { f.markPrepared(); return null; });
        filings.save(f);
        trail.record(t.getValue(), FILING, id, "PREPARED", "Ready to submit", uid, uname);
        return view(t, f);
    }

    @Transactional
    public FilingResponse submitFiling(TenantId t, UUID id, FilingSubmitRequest r, UUID uid, String uname) {
        Filing f = loadFiling(t, id);
        List<Map<String, Object>> snapshot = liveProof(t, id).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id().toString());
            m.put("fileName", e.fileName());
            m.put("fileSizeBytes", e.fileSizeBytes());
            m.put("uploadedByName", e.uploadedByName());
            return m;
        }).toList();
        guarded(() -> { f.markSubmitted(r.submittedOn(), r.submittedBy(), snapshot, LocalDate.now()); return null; });
        filings.save(f);
        trail.record(t.getValue(), FILING, id, "SUBMITTED", "On " + r.submittedOn() + " by " + r.submittedBy().trim() + ", " + snapshot.size() + " proof file(s)", uid, uname);
        return view(t, f);
    }

    @Transactional
    public FilingResponse acknowledgeFiling(TenantId t, UUID id, RegulatorRefRequest r, UUID uid, String uname) {
        Filing f = loadFiling(t, id);
        guarded(() -> { f.acknowledge(r.regulatorReference()); return null; });
        filings.save(f);
        trail.record(t.getValue(), FILING, id, "ACKNOWLEDGED", "Regulator reference " + r.regulatorReference().trim(), uid, uname);
        return view(t, f);
    }

    @Transactional(readOnly = true)
    public List<FilingResponse> listFilings(TenantId t, boolean includeDone) { return filings.search(t, includeDone).stream().map(f -> view(t, f)).toList(); }

    @Transactional(readOnly = true)
    public FilingSummary filingSummary(TenantId t) {
        List<Filing> all = filings.search(t, true);
        LocalDate today = LocalDate.now();
        List<Filing> pending = all.stream().filter(f -> f.getStatus() == FilingStatus.PLANNED || f.getStatus() == FilingStatus.PREPARED).toList();
        return new FilingSummary(pending.size(), pending.stream().filter(f -> PrivacyPartyRules.filingOverdue(f.getDueDate(), false, today)).count(),
                pending.stream().filter(f -> PrivacyPartyRules.filingDueSoon(f.getDueDate(), false, today)).count(), all.size() - pending.size());
    }

    /** Files currently attached to the filing (before submission these are what will be snapshotted as proof). */
    @Transactional(readOnly = true)
    public List<EvidenceRef> filingFiles(TenantId t, UUID id) {
        loadFiling(t, id);
        return liveProof(t, id).stream().map(e -> new EvidenceRef(e.id(), e.fileName(), e.fileSizeBytes(), e.uploadedByName())).toList();
    }

    private Filing loadFiling(TenantId t, UUID id) { return filings.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Filing", id.toString())); }

    private List<EvidenceResponse> liveProof(TenantId t, UUID id) { return evidence.listFor(t, SOURCE_MODULE, FILING, id); }

    @SuppressWarnings("unchecked")
    private FilingResponse view(TenantId t, Filing f) {
        LocalDate today = LocalDate.now();
        boolean done = f.getStatus() == FilingStatus.SUBMITTED || f.getStatus() == FilingStatus.ACKNOWLEDGED;
        List<EvidenceRef> proof = f.getProofSnapshot() == null ? List.of() : f.getProofSnapshot().stream()
                .map(m -> new EvidenceRef(UUID.fromString((String) m.get("id")), (String) m.get("fileName"), ((Number) m.get("fileSizeBytes")).longValue(), (String) m.get("uploadedByName"))).toList();
        int live = done ? proof.size() : liveProof(t, f.getId()).size();
        return new FilingResponse(f.getId(), f.getReference(), f.getFilingType(), f.getTitle(), f.getPeriodLabel(), f.getDueDate(), f.getOwnerName(), f.getStatus(), f.getSubmittedOn(),
                f.getSubmittedByName(), f.getRegulatorReference(), proof, live, f.getNotes(), PrivacyPartyRules.filingOverdue(f.getDueDate(), done, today), PrivacyPartyRules.filingDueSoon(f.getDueDate(), done, today));
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
