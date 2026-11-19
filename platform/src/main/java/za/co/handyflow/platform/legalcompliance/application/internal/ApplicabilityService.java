package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.application.internal.ApplicabilityRules.Conditions;
import za.co.handyflow.platform.legalcompliance.application.internal.ApplicabilityRules.Profile;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.ApplicabilityDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.RegulatoryLibraryDtos.RequirementResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Organisation profile + applicability assessments.
 * <ul>
 *  <li>Nothing is assumed applicable. The worklist shows a SUGGESTION and the reasons; a person decides.</li>
 *  <li>Only requirements in a CURRENT version can be assessed — never withdrawn or superseded text.</li>
 *  <li>APPLICABLE is approved at once. NOT_APPLICABLE waits for approval; a different approver is required
 *      when app.legalcompliance.sod.enforce=true.</li>
 *  <li>A new approved decision supersedes the old one; old rows are kept, so every past decision stays on record.</li>
 *  <li>Approving NOT_APPLICABLE while a live obligation exists needs retireLinkedObligation=true, and the retirement is audited.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ApplicabilityService {

    private static final int MIN_JUSTIFICATION = 15;
    private static final String ASSESSMENT = "ASSESSMENT";

    private final OrganisationProfileRepository profiles;
    private final ApplicabilityAssessmentRepository assessments;
    private final RegulatoryRequirementRepository requirements;
    private final RegulatoryVersionRepository versions;
    private final RegulatoryInstrumentRepository instruments;
    private final RegulatoryObligationLinkRepository obligations;
    private final RegulatoryAuditService audit;

    @Value("${app.legalcompliance.sod.enforce:false}")
    private boolean enforceSegregationOfDuties;

    // ── Profile ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(TenantId tenantId) {
        return profiles.findForTenant(tenantId).map(ProfileResponse::of).orElseGet(ProfileResponse::empty);
    }

    @Transactional
    public ProfileResponse saveProfile(TenantId tenantId, UUID userId, String userName, ProfileRequest req) {
        if (req.employeeCount() != null && req.employeeCount() < 0) throw bad("employeeCount cannot be negative");
        OrganisationProfile p = profiles.findForTenant(tenantId).orElseGet(() -> OrganisationProfile.create(tenantId));
        p.update(req.industries(), req.activities(), req.employeeCount(), req.entityType(), userId);
        profiles.save(p);
        audit.record(tenantId.getValue(), "PROFILE", p.getId(), "UPDATED", "Organisation profile updated", userId, userName);
        return ProfileResponse.of(p);
    }

    // ── Worklist ──────────────────────────────────────────────────────────────

    /** filter: ALL | UNASSESSED | PENDING | APPLICABLE | NOT_APPLICABLE */
    @Transactional(readOnly = true)
    public List<WorklistRow> worklist(TenantId tenantId, String filter) {
        String f = filter == null ? "ALL" : filter.toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "UNASSESSED", "PENDING", "APPLICABLE", "NOT_APPLICABLE").contains(f)) throw bad("Unknown filter " + filter);

        UUID tid = tenantId.getValue();
        Profile profile = profiles.findForTenant(tenantId)
                .map(p -> new Profile(new HashSet<>(p.getIndustries()), new HashSet<>(p.getActivities()), p.getEmployeeCount()))
                .orElseGet(() -> new Profile(Set.of(), Set.of(), null));

        Map<UUID, ApplicabilityAssessment> approved = new HashMap<>(), pending = new HashMap<>();
        for (ApplicabilityAssessment a : assessments.findByStatuses(tenantId, List.of(AssessmentStatus.APPROVED, AssessmentStatus.PENDING_APPROVAL)))
            (a.getStatus() == AssessmentStatus.APPROVED ? approved : pending).put(a.getRequirementId(), a);

        Map<UUID, UUID> obligationByRequirement = new HashMap<>();
        obligations.findAllLiveLinked(tenantId).forEach(o -> obligationByRequirement.put(o.getRequirementId(), o.getId()));

        Map<UUID, RegulatoryInstrument> instrumentCache = new HashMap<>();
        Map<UUID, RegulatoryVersion> versionCache = new HashMap<>();
        List<WorklistRow> rows = new ArrayList<>();

        for (RegulatoryRequirement r : requirements.findCurrentVisible(tid, VersionStatus.CURRENT)) {
            var s = ApplicabilityRules.suggest(profile, new Conditions(r.getMinEmployees(), new HashSet<>(r.getIndustries()), new HashSet<>(r.getActivities())));
            ApplicabilityAssessment shown = pending.getOrDefault(r.getId(), approved.get(r.getId()));
            String state = pending.containsKey(r.getId()) ? "PENDING"
                    : approved.containsKey(r.getId()) ? approved.get(r.getId()).getDecision().name() : "UNASSESSED";
            if (!f.equals("ALL") && !f.equals(state)) continue;

            RegulatoryInstrument i = instrumentCache.computeIfAbsent(r.getInstrumentId(), id -> instruments.findById(id).orElseThrow());
            RegulatoryVersion v = versionCache.computeIfAbsent(r.getVersionId(), id -> versions.findById(id).orElseThrow());
            rows.add(new WorklistRow(RequirementResponse.of(r), i.getTitle(), i.getIdentifier(), v.getVersionLabel(), s.suggestion(),
                    s.reasons(), state, shown == null ? null : AssessmentResponse.of(shown), obligationByRequirement.get(r.getId())));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponse> history(TenantId tenantId, UUID requirementId) {
        requirements.findVisible(tenantId.getValue(), requirementId).orElseThrow(() -> notFound("Requirement", requirementId));
        return assessments.history(tenantId, requirementId).stream().map(AssessmentResponse::of).toList();
    }

    // ── Decide ────────────────────────────────────────────────────────────────

    @Transactional
    public AssessmentResponse assess(TenantId tenantId, UUID userId, String userName, AssessRequest req) {
        UUID tid = tenantId.getValue();
        if (req.justification() == null || req.justification().trim().length() < MIN_JUSTIFICATION)
            throw bad("A justification of at least " + MIN_JUSTIFICATION + " characters is required");

        RegulatoryRequirement r = requirements.findVisible(tid, req.requirementId()).orElseThrow(() -> notFound("Requirement", req.requirementId()));
        RegulatoryVersion v = versions.findVisible(tid, r.getVersionId()).orElseThrow(() -> notFound("Version", r.getVersionId()));
        if (v.getStatus() != VersionStatus.CURRENT)
            throw conflict("REQUIREMENT_NOT_CURRENT", "Only requirements in the CURRENT version of an instrument can be assessed");
        if (assessments.findOne(tenantId, r.getId(), AssessmentStatus.PENDING_APPROVAL).isPresent())
            throw conflict("PENDING_EXISTS", "A decision on this requirement is already waiting for approval");

        Profile profile = profiles.findForTenant(tenantId)
                .map(p -> new Profile(new HashSet<>(p.getIndustries()), new HashSet<>(p.getActivities()), p.getEmployeeCount()))
                .orElseGet(() -> new Profile(Set.of(), Set.of(), null));
        var hint = ApplicabilityRules.suggest(profile, new Conditions(r.getMinEmployees(), new HashSet<>(r.getIndustries()), new HashSet<>(r.getActivities())));

        ApplicabilityAssessment a = ApplicabilityAssessment.create(tenantId, r.getId(), v.getId(), req.decision(), req.justification().trim(),
                hint.suggestion(), hint.reasons(), userId, userName);
        // Free the one-APPROVED slot BEFORE the new APPROVED row is written (partial unique index).
        if (a.getStatus() == AssessmentStatus.APPROVED) supersedePreviousApproved(tenantId, r.getId(), a.getId(), userId, userName);
        assessments.save(a);
        audit.record(tid, ASSESSMENT, a.getId(), "ASSESSED", req.decision() + " — " + r.getReference() + ": " + req.justification().trim()
                + " (system suggestion was " + hint.suggestion() + ")", userId, userName);
        return AssessmentResponse.of(a);
    }

    @Transactional
    public AssessmentResponse approve(TenantId tenantId, UUID assessmentId, UUID userId, String userName, ApproveRequest req) {
        UUID tid = tenantId.getValue();
        ApplicabilityAssessment a = assessments.findForTenant(tenantId, assessmentId).orElseThrow(() -> notFound("Assessment", assessmentId));
        if (a.getStatus() != AssessmentStatus.PENDING_APPROVAL)
            throw conflict("INVALID_STATE", "Only a PENDING_APPROVAL assessment can be approved — current: " + a.getStatus());
        if (enforceSegregationOfDuties && userId != null && userId.equals(a.getAssessedBy()))
            throw new HandyFlowException("Segregation of duties: you cannot approve your own assessment", HttpStatus.FORBIDDEN, "SOD_VIOLATION");

        var live = obligations.findLiveByRequirement(tenantId, a.getRequirementId());
        if (live.isPresent()) {
            if (!req.retireLinkedObligation())
                throw conflict("OBLIGATION_ACTIVE", "An active obligation was generated from this requirement. Approve with retireLinkedObligation=true to retire it");
            live.get().softDelete(userId);
            obligations.save(live.get());
            audit.record(tid, "OBLIGATION", live.get().getId(), "RETIRED", "Requirement assessed NOT_APPLICABLE (assessment " + assessmentId
                    + "): " + a.getJustification(), userId, userName);
        }
        // Supersede the old decision first, flushed, so the one-APPROVED index is never violated.
        supersedePreviousApproved(tenantId, a.getRequirementId(), a.getId(), userId, userName);
        a.approve(userId, userName, req.note());
        assessments.save(a);
        audit.record(tid, ASSESSMENT, assessmentId, "APPROVED", req.note() == null ? "Approved" : req.note(), userId, userName);
        return AssessmentResponse.of(a);
    }

    @Transactional
    public AssessmentResponse reject(TenantId tenantId, UUID assessmentId, UUID userId, String userName, RejectRequest req) {
        ApplicabilityAssessment a = assessments.findForTenant(tenantId, assessmentId).orElseThrow(() -> notFound("Assessment", assessmentId));
        if (a.getStatus() != AssessmentStatus.PENDING_APPROVAL)
            throw conflict("INVALID_STATE", "Only a PENDING_APPROVAL assessment can be rejected — current: " + a.getStatus());
        a.reject(userId, userName, req.reason().trim());
        assessments.save(a);
        audit.record(tenantId.getValue(), ASSESSMENT, assessmentId, "REJECTED", req.reason().trim(), userId, userName);
        return AssessmentResponse.of(a);
    }

    private void supersedePreviousApproved(TenantId tenantId, UUID requirementId, UUID newId, UUID userId, String userName) {
        assessments.findOne(tenantId, requirementId, AssessmentStatus.APPROVED).filter(x -> !x.getId().equals(newId)).ifPresent(old -> {
            old.supersede();
            assessments.saveAndFlush(old);   // free the one-APPROVED slot first
            audit.record(tenantId.getValue(), ASSESSMENT, old.getId(), "SUPERSEDED", "Superseded by assessment " + newId, userId, userName);
        });
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "VALIDATION"); }
    private static HandyFlowException conflict(String code, String m) { return new HandyFlowException(m, HttpStatus.CONFLICT, code); }
    private static ResourceNotFoundException notFound(String what, UUID id) { return new ResourceNotFoundException(what, id.toString()); }
}
