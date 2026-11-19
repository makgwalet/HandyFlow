package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.EvidenceRef;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.LicenceDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Licences, permits, certificates, registrations and accreditations, with their conditions and renewal.
 * "Expired" is never stored: it is worked out from the expiry date and today, so a licence cannot silently remain "active" after it lapses.
 * Renewal is a short flow: start → submit (proof of submission required; the files attached at that moment are snapshotted onto the licence) → record the
 * renewed licence (new expiry strictly later than the old). Each completed renewal is appended to the licence's history.
 * Files are held by the shared evidence module under entity types LICENCE (the licence document itself) and LICENCE_RENEWAL (renewal paperwork).
 */
@Service
@RequiredArgsConstructor
public class LicenceService {

    static final String SOURCE_MODULE = "legalcompliance";
    static final String ENTITY = "LICENCE", RENEWAL_ENTITY = "LICENCE_RENEWAL";

    private final LicenceRepository licences;
    private final LicenceConditionRepository conditions;
    private final RegulatoryObligationRepository obligations;
    private final EvidenceFacade evidence;
    private final RegulatoryAuditService trail;

    @Transactional
    public LicenceDetail create(TenantId t, LicenceRequest r, UUID uid, String uname) {
        checkObligation(t, r.obligationId());
        Licence l = guarded(() -> Licence.create(t, String.format("LIC-%05d", licences.nextRef()), r.licenceType(), r.title(), r.issuingAuthority(), r.licenceNumber(), r.scope(),
                r.issueDate(), r.expiryDate(), r.ownerName(), r.renewalLeadDays() == 0 ? 90 : r.renewalLeadDays(), r.obligationId(), r.notes()));
        licences.save(l);
        trail.record(t.getValue(), ENTITY, l.getId(), "CREATED", l.getReference() + " " + l.getTitle() + (l.getExpiryDate() == null ? "" : ", expires " + l.getExpiryDate()), uid, uname);
        return detail(t, l);
    }

    @Transactional
    public LicenceDetail update(TenantId t, UUID id, LicenceRequest r, UUID uid, String uname) {
        Licence l = load(t, id);
        checkObligation(t, r.obligationId());
        guarded(() -> { l.update(r.licenceType(), r.title(), r.issuingAuthority(), r.licenceNumber(), r.scope(), r.issueDate(), r.expiryDate(), r.ownerName(),
                r.renewalLeadDays() == 0 ? l.getRenewalLeadDays() : r.renewalLeadDays(), r.obligationId(), r.notes()); return null; });
        licences.save(l);
        trail.record(t.getValue(), ENTITY, id, "UPDATED", "Details changed" + (l.getExpiryDate() == null ? "" : ", expires " + l.getExpiryDate()), uid, uname);
        return detail(t, l);
    }

    @Transactional
    public LicenceDetail startRenewal(TenantId t, UUID id, UUID uid, String uname) {
        Licence l = load(t, id);
        guarded(() -> { l.startRenewal(LocalDate.now()); return null; });
        licences.save(l);
        trail.record(t.getValue(), ENTITY, id, "RENEWAL_STARTED", "Current expiry " + l.getExpiryDate(), uid, uname);
        return detail(t, l);
    }

    @Transactional
    public LicenceDetail abandonRenewal(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        Licence l = load(t, id);
        guarded(() -> { l.abandonRenewal(r.reason()); return null; });
        licences.save(l);
        trail.record(t.getValue(), ENTITY, id, "RENEWAL_ABANDONED", r.reason().trim(), uid, uname);
        return detail(t, l);
    }

    @Transactional
    public LicenceDetail submitRenewal(TenantId t, UUID id, RenewalSubmitRequest r, UUID uid, String uname) {
        Licence l = load(t, id);
        List<Map<String, Object>> proof = evidence.listFor(t, SOURCE_MODULE, RENEWAL_ENTITY, id).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id().toString());
            m.put("fileName", e.fileName());
            m.put("fileSizeBytes", e.fileSizeBytes());
            m.put("uploadedByName", e.uploadedByName());
            return m;
        }).collect(Collectors.toList());
        guarded(() -> { l.submitRenewal(r.submittedOn(), r.reference(), proof, LocalDate.now()); return null; });
        licences.save(l);
        trail.record(t.getValue(), ENTITY, id, "RENEWAL_SUBMITTED", "Submitted " + r.submittedOn() + ", " + proof.size() + " proof file(s)" + (l.getRenewalReference() == null ? "" : ", ref " + l.getRenewalReference()), uid, uname);
        return detail(t, l);
    }

    @Transactional
    public LicenceDetail recordRenewed(TenantId t, UUID id, RenewedRequest r, UUID uid, String uname) {
        Licence l = load(t, id);
        LocalDate previous = l.getExpiryDate();
        guarded(() -> { l.recordRenewed(r.newIssueDate(), r.newExpiryDate(), r.newLicenceNumber(), uname, LocalDate.now()); return null; });
        licences.save(l);
        trail.record(t.getValue(), ENTITY, id, "RENEWED", "Expiry " + previous + " → " + l.getExpiryDate(), uid, uname);
        return detail(t, l);
    }

    @Transactional
    public LicenceDetail suspend(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        Licence l = load(t, id);
        guarded(() -> { l.suspend(r.reason()); return null; });
        licences.save(l);
        trail.record(t.getValue(), ENTITY, id, "SUSPENDED", r.reason().trim(), uid, uname);
        return detail(t, l);
    }

    @Transactional
    public LicenceDetail reinstate(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        Licence l = load(t, id);
        guarded(() -> { l.reinstate(r.reason()); return null; });
        licences.save(l);
        trail.record(t.getValue(), ENTITY, id, "REINSTATED", r.reason().trim(), uid, uname);
        return detail(t, l);
    }

    @Transactional
    public LicenceDetail revoke(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        Licence l = load(t, id);
        guarded(() -> { l.revoke(r.reason()); return null; });
        licences.save(l);
        trail.record(t.getValue(), ENTITY, id, "REVOKED", r.reason().trim(), uid, uname);
        return detail(t, l);
    }

    @Transactional
    public LicenceDetail surrender(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        Licence l = load(t, id);
        guarded(() -> { l.surrender(r.reason()); return null; });
        licences.save(l);
        trail.record(t.getValue(), ENTITY, id, "SURRENDERED", r.reason().trim(), uid, uname);
        return detail(t, l);
    }

    // ── Conditions ──

    @Transactional
    public LicenceDetail addCondition(TenantId t, UUID id, ConditionRequest r, UUID uid, String uname) {
        Licence l = openLicence(t, id);
        LicenceCondition c = guarded(() -> LicenceCondition.add(t, id, r.description(), r.dueDate(), r.frequencyMonths()));
        conditions.save(c);
        trail.record(t.getValue(), ENTITY, id, "CONDITION_ADDED", c.getDescription(), uid, uname);
        return detail(t, l);
    }

    @Transactional
    public LicenceDetail updateCondition(TenantId t, UUID id, UUID cid, ConditionRequest r, UUID uid, String uname) {
        Licence l = openLicence(t, id);
        LicenceCondition c = loadCondition(t, id, cid);
        guarded(() -> { c.update(r.description(), r.dueDate(), r.frequencyMonths()); return null; });
        conditions.save(c);
        trail.record(t.getValue(), ENTITY, id, "CONDITION_UPDATED", c.getDescription(), uid, uname);
        return detail(t, l);
    }

    @Transactional
    public LicenceDetail meetCondition(TenantId t, UUID id, UUID cid, MetRequest r, UUID uid, String uname) {
        Licence l = openLicence(t, id);
        LicenceCondition c = loadCondition(t, id, cid);
        LocalDate next = c.getFrequencyMonths() == null || r.metOn() == null ? null : LicenceRules.nextConditionDue(c.getDueDate(), c.getFrequencyMonths(), r.metOn());
        guarded(() -> { c.markMet(r.metOn(), r.note(), LocalDate.now(), next); return null; });
        conditions.save(c);
        trail.record(t.getValue(), ENTITY, id, "CONDITION_MET", c.getDescription() + (c.getFrequencyMonths() == null ? "" : ", next due " + c.getDueDate()), uid, uname);
        return detail(t, l);
    }

    @Transactional
    public LicenceDetail breachCondition(TenantId t, UUID id, UUID cid, ReasonRequest r, UUID uid, String uname) {
        Licence l = openLicence(t, id);
        LicenceCondition c = loadCondition(t, id, cid);
        guarded(() -> { c.markBreached(r.reason()); return null; });
        conditions.save(c);
        trail.record(t.getValue(), ENTITY, id, "CONDITION_BREACHED", c.getDescription() + ": " + r.reason().trim(), uid, uname);
        return detail(t, l);
    }

    @Transactional
    public LicenceDetail removeCondition(TenantId t, UUID id, UUID cid, UUID uid, String uname) {
        Licence l = openLicence(t, id);
        LicenceCondition c = loadCondition(t, id, cid);
        conditions.delete(c);
        trail.record(t.getValue(), ENTITY, id, "CONDITION_REMOVED", c.getDescription(), uid, uname);
        return detail(t, l);
    }

    // ── Reads ──

    @Transactional(readOnly = true)
    public List<LicenceResponse> list(TenantId t, LicenceStatus status, boolean includeEnded) {
        List<Licence> ls = licences.search(t, status, includeEnded || status == LicenceStatus.REVOKED || status == LicenceStatus.SURRENDERED);
        Map<UUID, List<LicenceCondition>> byLicence = ls.isEmpty() ? Map.of()
                : conditions.findForLicences(t, ls.stream().map(Licence::getId).toList()).stream().collect(Collectors.groupingBy(LicenceCondition::getLicenceId));
        return ls.stream().map(l -> view(t, l, byLicence.getOrDefault(l.getId(), List.of()), false)).toList();
    }

    @Transactional(readOnly = true)
    public LicenceDetail get(TenantId t, UUID id) { return detail(t, load(t, id)); }

    @Transactional(readOnly = true)
    public LicenceSummary summary(TenantId t) {
        LocalDate today = LocalDate.now();
        List<Licence> ls = licences.search(t, null, false);
        Map<UUID, Licence> byId = ls.stream().collect(Collectors.toMap(Licence::getId, l -> l));
        List<LicenceCondition> cs = ls.isEmpty() ? List.of() : conditions.findForLicences(t, byId.keySet());
        long expired = ls.stream().filter(l -> inForce(l) && LicenceRules.expired(l.getExpiryDate(), today)).count();
        long valid = ls.stream().filter(l -> l.getStatus() == LicenceStatus.ACTIVE && !LicenceRules.expired(l.getExpiryDate(), today)).count();
        long expiring = ls.stream().filter(l -> inForce(l) && !LicenceRules.expired(l.getExpiryDate(), today) && LicenceRules.expiringSoon(l.getExpiryDate(), l.getRenewalLeadDays(), today)).count();
        return new LicenceSummary(ls.size(), valid, expiring, expired, ls.stream().filter(l -> l.getStatus() == LicenceStatus.RENEWAL_IN_PROGRESS).count(),
                ls.stream().filter(l -> l.getStatus() == LicenceStatus.SUSPENDED).count(),
                cs.stream().filter(c -> c.getStatus() == ConditionStatus.PENDING && LicenceRules.conditionOverdue(c.getDueDate(), today)).count(),
                cs.stream().filter(c -> c.getStatus() == ConditionStatus.BREACHED).count());
    }

    /** Files currently attached under one of this module's licence entity types (LICENCE or LICENCE_RENEWAL). */
    @Transactional(readOnly = true)
    public List<EvidenceRef> files(TenantId t, UUID id, boolean renewal) {
        load(t, id);
        return evidence.listFor(t, SOURCE_MODULE, renewal ? RENEWAL_ENTITY : ENTITY, id).stream()
                .map(e -> new EvidenceRef(e.id(), e.fileName(), e.fileSizeBytes(), e.uploadedByName())).toList();
    }

    // ── internals ──

    private static boolean inForce(Licence l) { return l.getStatus() == LicenceStatus.ACTIVE || l.getStatus() == LicenceStatus.RENEWAL_IN_PROGRESS; }

    private Licence load(TenantId t, UUID id) { return licences.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Licence", id.toString())); }

    private Licence openLicence(TenantId t, UUID id) {
        Licence l = load(t, id);
        if (l.isFinal()) throw new HandyFlowException("A revoked or surrendered licence cannot be changed", HttpStatus.CONFLICT, "INVALID_STATE");
        return l;
    }

    private LicenceCondition loadCondition(TenantId t, UUID licenceId, UUID cid) {
        LicenceCondition c = conditions.findForTenant(t, cid).orElseThrow(() -> new ResourceNotFoundException("Condition", cid.toString()));
        if (!c.getLicenceId().equals(licenceId)) throw new ResourceNotFoundException("Condition", cid.toString());
        return c;
    }

    private void checkObligation(TenantId t, UUID obligationId) {
        if (obligationId != null && obligations.findActiveById(t, obligationId).isEmpty())
            throw new HandyFlowException("The linked obligation was not found", HttpStatus.BAD_REQUEST, "VALIDATION");
    }

    private LicenceDetail detail(TenantId t, Licence l) {
        List<LicenceCondition> cs = conditions.findForLicence(t, l.getId());
        LocalDate today = LocalDate.now();
        return new LicenceDetail(view(t, l, cs, true), cs.stream().map(c -> new ConditionResponse(c.getId(), c.getDescription(), c.getDueDate(), c.getFrequencyMonths(), c.getStatus(), c.getLastMetOn(), c.getNote(),
                c.getStatus() == ConditionStatus.PENDING && LicenceRules.conditionOverdue(c.getDueDate(), today),
                c.getStatus() == ConditionStatus.PENDING && LicenceRules.conditionDueSoon(c.getDueDate(), today))).toList());
    }

    private LicenceResponse view(TenantId t, Licence l, List<LicenceCondition> cs, boolean full) {
        LocalDate today = LocalDate.now();
        boolean expired = inForce(l) && LicenceRules.expired(l.getExpiryDate(), today);
        boolean soon = inForce(l) && !expired && LicenceRules.expiringSoon(l.getExpiryDate(), l.getRenewalLeadDays(), today);
        String obligationTitle = l.getObligationId() == null ? null : obligations.findActiveById(t, l.getObligationId()).map(RegulatoryObligation::getTitle).orElse(null);
        List<EvidenceRef> proof = new ArrayList<>();
        if (l.getRenewalProof() != null)
            for (Map<String, Object> m : l.getRenewalProof())
                proof.add(new EvidenceRef(UUID.fromString(String.valueOf(m.get("id"))), String.valueOf(m.get("fileName")),
                        m.get("fileSizeBytes") instanceof Number n ? n.longValue() : 0L, m.get("uploadedByName") == null ? null : String.valueOf(m.get("uploadedByName"))));
        int live = full && l.getStatus() == LicenceStatus.RENEWAL_IN_PROGRESS ? evidence.listFor(t, SOURCE_MODULE, RENEWAL_ENTITY, l.getId()).size() : 0;
        return new LicenceResponse(l.getId(), l.getReference(), l.getLicenceType(), l.getTitle(), l.getIssuingAuthority(), l.getLicenceNumber(), l.getScope(), l.getIssueDate(), l.getExpiryDate(),
                l.getStatus(), expired ? "EXPIRED" : l.getStatus().name(), l.getOwnerName(), l.getRenewalLeadDays(), l.getObligationId(), obligationTitle, l.getRenewalStartedOn(),
                l.getRenewalSubmittedOn(), l.getRenewalReference(), proof, live, l.getRenewalHistory(), l.getStatusReason(), l.getNotes(), expired, soon,
                LicenceRules.daysToExpiry(l.getExpiryDate(), today), cs.size(),
                (int) cs.stream().filter(c -> c.getStatus() == ConditionStatus.PENDING && LicenceRules.conditionOverdue(c.getDueDate(), today)).count(),
                (int) cs.stream().filter(c -> c.getStatus() == ConditionStatus.BREACHED).count());
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
