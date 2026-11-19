package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.application.internal.ChangeDetectionRules.Delta;
import za.co.handyflow.platform.legalcompliance.application.internal.ChangeDetectionRules.Kind;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.RegulatoryChangeDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

/**
 * Regulatory change management.
 * <p>
 * When a tenant-owned instrument gets a new CURRENT version (or its current version is repealed), this compares the old and
 * new requirements and, in the same transaction as the publish:
 * <ul>
 *  <li>UNCHANGED: carries the approved decision across to the new requirement as a NEW assessment (the old one is superseded,
 *      not edited) and re-points any generated obligation. Nobody re-does work for text that did not change.</li>
 *  <li>CHANGED / REMOVED where the organisation has a stake (a live obligation or an approved decision): creates an IMPACT that a
 *      person must resolve by a due date. The old approved decision stays in force until it is resolved.</li>
 *  <li>ADDED: creates an impact so the new duty is not silently missed.</li>
 *  <li>Any decision still awaiting approval on an old requirement is rejected with an explanatory note (it can no longer be approved
 *      against text that is no longer current).</li>
 * </ul>
 * Nothing is ever edited or deleted: decisions are superseded, obligations are retired with an audit event.
 */
@Service
@RequiredArgsConstructor
public class RegulatoryChangeService {

    private static final String CHANGE = "CHANGE";
    private static final int MIN_JUSTIFICATION = 15;

    private final RegulatoryChangeRepository changes;
    private final RegulatoryImpactRepository impacts;
    private final RegulatoryRequirementRepository requirements;
    private final RegulatoryVersionRepository versions;
    private final ApplicabilityAssessmentRepository assessments;
    private final RegulatoryObligationLinkRepository obligations;
    private final RegulatoryAuditService audit;

    @Value("${app.legalcompliance.change.impact-days:30}")
    private int impactDays;

    // ── Detection (called from RegulatoryLibraryService in the publish / repeal transaction) ──

    /** previous == null means this is the first published version: nothing to compare, no change record. */
    @Transactional
    public RegulatoryChange onPublished(TenantId tenantId, RegulatoryInstrument instrument, RegulatoryVersion previous,
                                        RegulatoryVersion published, UUID userId, String userName) {
        if (previous == null) return null;
        List<RegulatoryRequirement> oldReqs = requirements.findByVersion(previous.getId());
        List<RegulatoryRequirement> newReqs = requirements.findByVersion(published.getId());
        Map<UUID, RegulatoryRequirement> oldById = index(oldReqs), newById = index(newReqs);

        List<Delta> deltas = ChangeDetectionRules.diff(oldReqs.stream().map(RegulatoryChangeService::toReq).toList(),
                newReqs.stream().map(RegulatoryChangeService::toReq).toList());

        int added = 0, changed = 0, removed = 0, carried = 0;
        List<RegulatoryImpact> toSave = new ArrayList<>();
        RegulatoryChange change = RegulatoryChange.create(tenantId, instrument.getId(), instrument.getTitle(), previous.getId(),
                previous.getVersionLabel(), published.getId(), published.getVersionLabel(), ChangeKind.PUBLISHED, published.getSummary(),
                0, 0, 0, 0, userId, userName);
        LocalDate due = LocalDate.now().plusDays(impactDays);

        for (Delta d : deltas) {
            if (d.oldId() != null && d.kind() != Kind.UNCHANGED) rejectPendingOnOld(tenantId, d.oldId(), published.getVersionLabel(), userId, userName);
            switch (d.kind()) {
                case UNCHANGED -> {
                    carryOver(tenantId, oldById.get(d.oldId()), newById.get(d.newId()), previous, published, userId, userName);
                    carried++;
                }
                case ADDED -> {
                    added++;
                    toSave.add(RegulatoryImpact.create(tenantId, change.getId(), ImpactType.ADDED, null, d.newId(), d.reference(), d.title(),
                            "New requirement in " + published.getVersionLabel() + " — assess whether it applies", null, null, due));
                }
                case CHANGED -> {
                    changed++;
                    stake(tenantId, d.oldId()).ifPresent(s -> toSave.add(RegulatoryImpact.create(tenantId, change.getId(), ImpactType.CHANGED,
                            d.oldId(), d.newId(), d.reference(), d.title(), d.summary(), s.obligationId(), s.assessmentId(), due)));
                }
                case REMOVED -> {
                    removed++;
                    stake(tenantId, d.oldId()).ifPresent(s -> toSave.add(RegulatoryImpact.create(tenantId, change.getId(), ImpactType.REMOVED,
                            d.oldId(), null, d.reference(), d.title(), "No longer in " + published.getVersionLabel(), s.obligationId(),
                            s.assessmentId(), due)));
                }
                default -> { }
            }
        }
        return persist(tenantId, change, toSave, added, changed, removed, carried,
                "Version " + published.getVersionLabel() + " published over " + previous.getVersionLabel() + ": " + added + " added, " + changed
                        + " changed, " + removed + " removed, " + carried + " carried forward unchanged; " + toSave.size() + " impact(s) to review",
                userId, userName);
    }

    /** The CURRENT version was repealed: every requirement the organisation had a stake in becomes a REMOVED impact. */
    @Transactional
    public RegulatoryChange onRepealed(TenantId tenantId, RegulatoryInstrument instrument, RegulatoryVersion repealed, String reason,
                                       UUID userId, String userName) {
        List<RegulatoryRequirement> reqs = requirements.findByVersion(repealed.getId());
        RegulatoryChange change = RegulatoryChange.create(tenantId, instrument.getId(), instrument.getTitle(), repealed.getId(),
                repealed.getVersionLabel(), null, null, ChangeKind.REPEALED, reason, 0, 0, 0, 0, userId, userName);
        LocalDate due = LocalDate.now().plusDays(impactDays);
        List<RegulatoryImpact> toSave = new ArrayList<>();
        for (RegulatoryRequirement r : reqs) {
            rejectPendingOnOld(tenantId, r.getId(), "repeal of " + repealed.getVersionLabel(), userId, userName);
            stake(tenantId, r.getId()).ifPresent(s -> toSave.add(RegulatoryImpact.create(tenantId, change.getId(), ImpactType.REMOVED, r.getId(), null,
                    r.getReference(), r.getTitle(), "Version " + repealed.getVersionLabel() + " repealed: " + reason, s.obligationId(), s.assessmentId(), due)));
        }
        return persist(tenantId, change, toSave, 0, 0, reqs.size(), 0,
                "Version " + repealed.getVersionLabel() + " repealed (" + reason + "): " + toSave.size() + " impact(s) to review", userId, userName);
    }

    // ── Reading ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ChangeResponse> list(TenantId tenantId, ChangeStatus status, Pageable pageable) {
        return changes.findPageForTenant(tenantId, status, pageable).map(ChangeResponse::of);
    }

    @Transactional(readOnly = true)
    public ChangeDetailResponse get(TenantId tenantId, UUID id) {
        RegulatoryChange c = changes.findOneForTenant(tenantId, id).orElseThrow(() -> notFound("Change", id));
        LocalDate today = LocalDate.now();
        return new ChangeDetailResponse(ChangeResponse.of(c), impacts.findByChange(tenantId, id).stream().map(i -> ImpactResponse.of(i, c, today)).toList());
    }

    @Transactional(readOnly = true)
    public List<ImpactResponse> listImpacts(TenantId tenantId, ImpactStatus status) {
        LocalDate today = LocalDate.now();
        Map<UUID, RegulatoryChange> cache = new HashMap<>();
        return impacts.findByStatus(tenantId, status).stream()
                .map(i -> ImpactResponse.of(i, cache.computeIfAbsent(i.getChangeId(), cid -> changes.findOneForTenant(tenantId, cid).orElseThrow()), today)).toList();
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(TenantId tenantId) {
        LocalDate today = LocalDate.now();
        List<RegulatoryImpact> pending = impacts.findByStatus(tenantId, ImpactStatus.PENDING);
        long overdue = pending.stream().filter(i -> i.getDueDate().isBefore(today)).count();
        long soon = pending.stream().filter(i -> !i.getDueDate().isBefore(today) && !i.getDueDate().isAfter(today.plusDays(7))).count();
        return new SummaryResponse(pending.size(), overdue, soon, changes.findPageForTenant(tenantId, ChangeStatus.OPEN, Pageable.unpaged()).getTotalElements());
    }

    // ── Resolving ─────────────────────────────────────────────────────────────

    @Transactional
    public ImpactResponse resolve(TenantId tenantId, UUID impactId, UUID userId, String userName, ResolveRequest req) {
        RegulatoryImpact impact = impacts.findForTenant(tenantId, impactId).orElseThrow(() -> notFound("Impact", impactId));
        if (!impact.isPending()) throw conflict("INVALID_STATE", "This impact is already resolved");
        RegulatoryChange change = changes.findOneForTenant(tenantId, impact.getChangeId()).orElseThrow(() -> notFound("Change", impact.getChangeId()));
        String note = req.justification() == null ? "" : req.justification().trim();

        ImpactOutcome outcome = switch (impact.getImpactType()) {
            case ADDED -> resolveAdded(tenantId, impact, req);
            case CHANGED -> resolveChanged(tenantId, impact, req, note, userId, userName);
            case REMOVED -> resolveRemoved(tenantId, impact, req, note, userId, userName);
        };

        impact.resolve(outcome, note.isEmpty() ? null : note, userId, userName);
        impacts.save(impact);
        audit.record(tenantId.getValue(), CHANGE, change.getId(), "IMPACT_RESOLVED", impact.getReference() + " (" + impact.getImpactType() + ") → "
                + outcome + (note.isEmpty() ? "" : ": " + note), userId, userName);
        if (impacts.countPendingInChange(tenantId, change.getId()) == 0) {
            change.close();
            changes.save(change);
            audit.record(tenantId.getValue(), CHANGE, change.getId(), "CLOSED", "All impacts resolved", userId, userName);
        }
        return ImpactResponse.of(impact, change, LocalDate.now());
    }

    private ImpactOutcome resolveAdded(TenantId tenantId, RegulatoryImpact impact, ResolveRequest req) {
        if (req.action() != ImpactAction.ACKNOWLEDGE) throw bad("A new requirement is resolved with ACKNOWLEDGE, once it has been assessed");
        if (!assessed(tenantId, impact.getNewRequirementId()))
            throw conflict("NOT_ASSESSED", "Assess the new requirement on the Applicability tab first (applies / does not apply), then acknowledge");
        return ImpactOutcome.ASSESSED;
    }

    private ImpactOutcome resolveChanged(TenantId tenantId, RegulatoryImpact impact, ResolveRequest req, String note, UUID userId, String userName) {
        UUID newReqId = impact.getNewRequirementId();
        RegulatoryRequirement newReq = requirements.findVisible(tenantId.getValue(), newReqId).orElseThrow(() -> notFound("Requirement", newReqId));
        RegulatoryVersion newVer = versions.findVisible(tenantId.getValue(), newReq.getVersionId()).orElseThrow(() -> notFound("Version", newReq.getVersionId()));
        if (newVer.getStatus() != VersionStatus.CURRENT)
            throw conflict("VERSION_NOT_CURRENT", "The amended requirement's version is no longer current — review the newer change instead");
        var liveObligation = obligations.findLiveByRequirement(tenantId, impact.getOldRequirementId());
        var already = assessments.findOne(tenantId, newReqId, AssessmentStatus.APPROVED);
        boolean pendingNew = assessments.findOne(tenantId, newReqId, AssessmentStatus.PENDING_APPROVAL).isPresent();

        if (req.action() == ImpactAction.ACKNOWLEDGE) {
            if (already.isEmpty() && !pendingNew) throw conflict("NOT_ASSESSED", "The amended requirement has no decision yet — choose CONFIRM_APPLICABLE or NO_LONGER_APPLICABLE");
            if (liveObligation.isPresent()) {
                if (already.isEmpty() || already.get().getDecision() != ApplicabilityDecision.APPLICABLE)
                    throw conflict("OBLIGATION_ACTIVE", "A live obligation exists: confirm it applies, or approve the exclusion with the obligation retired");
                liveObligation.get().relinkRequirement(newReqId, newVer.getId(), already.get().getId());
                obligations.save(liveObligation.get());
            }
            supersedeOld(tenantId, impact.getOldAssessmentId());
            return ImpactOutcome.ASSESSED;
        }
        if (req.action() != ImpactAction.CONFIRM_APPLICABLE && req.action() != ImpactAction.NO_LONGER_APPLICABLE)
            throw bad("A changed requirement is resolved with CONFIRM_APPLICABLE, NO_LONGER_APPLICABLE or ACKNOWLEDGE");
        requireJustification(note);
        if (already.isPresent() || pendingNew)
            throw conflict("ALREADY_ASSESSED", "The amended requirement already has a decision — use ACKNOWLEDGE");

        supersedeOld(tenantId, impact.getOldAssessmentId());
        ApplicabilityDecision decision = req.action() == ImpactAction.CONFIRM_APPLICABLE ? ApplicabilityDecision.APPLICABLE : ApplicabilityDecision.NOT_APPLICABLE;
        ApplicabilityAssessment a = ApplicabilityAssessment.create(tenantId, newReqId, newVer.getId(), decision, note, null,
                List.of("Decided while resolving regulatory change impact " + impact.getId()), userId, userName);
        assessments.save(a);

        liveObligation.ifPresent(o -> {
            o.relinkRequirement(newReqId, newVer.getId(), decision == ApplicabilityDecision.APPLICABLE ? a.getId() : null);
            if (decision == ApplicabilityDecision.APPLICABLE && req.updateObligation())
                o.refreshFromRequirement(newReq.getTitle(), o.getRegulationReference(), newReq.getRequirementText(), newReq.getEvidenceExpectation());
            obligations.save(o);
        });
        if (decision == ApplicabilityDecision.NOT_APPLICABLE) return ImpactOutcome.REASSESSED_NOT_APPLICABLE;
        return liveObligation.isPresent() && req.updateObligation() ? ImpactOutcome.OBLIGATION_UPDATED
                : liveObligation.isPresent() ? ImpactOutcome.REASSESSED : ImpactOutcome.ASSESSED;
    }

    private ImpactOutcome resolveRemoved(TenantId tenantId, RegulatoryImpact impact, ResolveRequest req, String note, UUID userId, String userName) {
        var liveObligation = obligations.findLiveByRequirement(tenantId, impact.getOldRequirementId());
        switch (req.action()) {
            case RETIRE_OBLIGATION -> {
                requireJustification(note);
                if (liveObligation.isEmpty()) throw conflict("NO_OBLIGATION", "There is no live obligation to retire — use ACKNOWLEDGE");
                liveObligation.get().softDelete(userId);
                obligations.save(liveObligation.get());
                audit.record(tenantId.getValue(), "OBLIGATION", liveObligation.get().getId(), "RETIRED",
                        "Requirement " + impact.getReference() + " removed by regulatory change: " + note, userId, userName);
                supersedeOld(tenantId, impact.getOldAssessmentId());
                return ImpactOutcome.OBLIGATION_RETIRED;
            }
            case KEEP_OBLIGATION -> {
                requireJustification(note);
                if (liveObligation.isEmpty()) throw conflict("NO_OBLIGATION", "There is no live obligation to keep — use ACKNOWLEDGE");
                liveObligation.get().detachRequirement();
                obligations.save(liveObligation.get());
                audit.record(tenantId.getValue(), "OBLIGATION", liveObligation.get().getId(), "DETACHED",
                        "Kept as a hand-managed obligation after " + impact.getReference() + " was removed: " + note, userId, userName);
                supersedeOld(tenantId, impact.getOldAssessmentId());
                return ImpactOutcome.OBLIGATION_KEPT_MANUAL;
            }
            case ACKNOWLEDGE -> {
                if (liveObligation.isPresent())
                    throw conflict("OBLIGATION_ACTIVE", "A live obligation still exists — choose RETIRE_OBLIGATION or KEEP_OBLIGATION");
                supersedeOld(tenantId, impact.getOldAssessmentId());
                return ImpactOutcome.ACKNOWLEDGED;
            }
            default -> throw bad("A removed requirement is resolved with RETIRE_OBLIGATION, KEEP_OBLIGATION or ACKNOWLEDGE");
        }
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private record Stake(UUID obligationId, UUID assessmentId) {}

    /** An organisation has a stake in a requirement if it holds a live obligation for it or an approved decision on it. */
    private Optional<Stake> stake(TenantId tenantId, UUID requirementId) {
        UUID obligation = obligations.findLiveByRequirement(tenantId, requirementId).map(RegulatoryObligation::getId).orElse(null);
        UUID assessment = assessments.findOne(tenantId, requirementId, AssessmentStatus.APPROVED).map(ApplicabilityAssessment::getId).orElse(null);
        return obligation == null && assessment == null ? Optional.empty() : Optional.of(new Stake(obligation, assessment));
    }

    private void carryOver(TenantId tenantId, RegulatoryRequirement oldReq, RegulatoryRequirement newReq, RegulatoryVersion previous,
                           RegulatoryVersion published, UUID userId, String userName) {
        var pending = assessments.findOne(tenantId, oldReq.getId(), AssessmentStatus.PENDING_APPROVAL);
        if (pending.isPresent()) {      // an unchanged requirement keeps its waiting decision: same text, same question
            ApplicabilityAssessment p = pending.get();
            p.reject(userId, userName, "Moved to " + published.getVersionLabel() + " (requirement unchanged) — approval request re-opened");
            assessments.saveAndFlush(p);
            assessments.save(ApplicabilityAssessment.create(tenantId, newReq.getId(), published.getId(), p.getDecision(), p.getJustification(),
                    p.getSuggestion(), p.getSuggestionReasons(), p.getAssessedBy(), p.getAssessedByName()));
        }
        var approved = assessments.findOne(tenantId, oldReq.getId(), AssessmentStatus.APPROVED);
        UUID newAssessmentId = null;
        if (approved.isPresent()) {
            ApplicabilityAssessment old = approved.get();
            ApplicabilityAssessment moved = ApplicabilityAssessment.carryForward(tenantId, old, newReq.getId(), published.getId(), previous.getVersionLabel(), userId, userName);
            old.supersede();
            assessments.saveAndFlush(old);
            assessments.save(moved);
            newAssessmentId = moved.getId();
            audit.record(tenantId.getValue(), "ASSESSMENT", moved.getId(), "CARRIED_FORWARD", "From assessment " + old.getId() + " (" + previous.getVersionLabel()
                    + " → " + published.getVersionLabel() + ", requirement unchanged)", userId, userName);
        }
        UUID assessmentForObligation = newAssessmentId;
        obligations.findLiveByRequirement(tenantId, oldReq.getId()).ifPresent(o -> {
            o.relinkRequirement(newReq.getId(), published.getId(), assessmentForObligation);
            obligations.save(o);
        });
    }

    private void rejectPendingOnOld(TenantId tenantId, UUID oldRequirementId, String versionLabel, UUID userId, String userName) {
        assessments.findOne(tenantId, oldRequirementId, AssessmentStatus.PENDING_APPROVAL).ifPresent(p -> {
            p.reject(userId, userName, "Automatically rejected: the requirement changed in " + versionLabel + " — reassess against the new text");
            assessments.saveAndFlush(p);
            audit.record(tenantId.getValue(), "ASSESSMENT", p.getId(), "REJECTED", "Superseded by regulatory change " + versionLabel, userId, userName);
        });
    }

    private void supersedeOld(TenantId tenantId, UUID oldAssessmentId) {
        if (oldAssessmentId == null) return;
        assessments.findForTenant(tenantId, oldAssessmentId).filter(a -> a.getStatus() == AssessmentStatus.APPROVED).ifPresent(a -> {
            a.supersede();
            assessments.saveAndFlush(a);
        });
    }

    private boolean assessed(TenantId tenantId, UUID requirementId) {
        return assessments.findOne(tenantId, requirementId, AssessmentStatus.APPROVED).isPresent()
                || assessments.findOne(tenantId, requirementId, AssessmentStatus.PENDING_APPROVAL).isPresent();
    }

    private RegulatoryChange persist(TenantId tenantId, RegulatoryChange draft, List<RegulatoryImpact> toSave, int added, int changed, int removed,
                                     int carried, String summary, UUID userId, String userName) {
        RegulatoryChange c = RegulatoryChange.create(tenantId, draft.getInstrumentId(), draft.getInstrumentTitle(), draft.getFromVersionId(),
                draft.getFromLabel(), draft.getToVersionId(), draft.getToLabel(), draft.getChangeKind(), summary, added, changed, removed, carried,
                userId, userName);
        if (toSave.isEmpty()) c.close();      // nothing for a person to decide
        changes.save(c);
        for (RegulatoryImpact i : toSave) {
            impacts.save(RegulatoryImpact.create(tenantId, c.getId(), i.getImpactType(), i.getOldRequirementId(), i.getNewRequirementId(), i.getReference(),
                    i.getTitle(), i.getDiffSummary(), i.getObligationId(), i.getOldAssessmentId(), i.getDueDate()));
        }
        audit.record(tenantId.getValue(), CHANGE, c.getId(), c.isOpen() ? "RAISED" : "RAISED_AND_CLOSED", summary, userId, userName);
        return c;
    }

    private static Map<UUID, RegulatoryRequirement> index(List<RegulatoryRequirement> list) {
        Map<UUID, RegulatoryRequirement> m = new HashMap<>();
        list.forEach(r -> m.put(r.getId(), r));
        return m;
    }

    private static ChangeDetectionRules.Req toReq(RegulatoryRequirement r) {
        return new ChangeDetectionRules.Req(r.getId(), r.getPreviousRequirementId(), r.getReference(), r.getTitle(), r.getRequirementText(),
                r.getCategory().name(), r.getRecurrence().name(), r.getMinEmployees(), r.getIndustries(), r.getActivities(), r.getEvidenceExpectation());
    }

    private static void requireJustification(String note) {
        if (note.length() < MIN_JUSTIFICATION) throw bad("A justification of at least " + MIN_JUSTIFICATION + " characters is required");
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "VALIDATION"); }
    private static HandyFlowException conflict(String code, String m) { return new HandyFlowException(m, HttpStatus.CONFLICT, code); }
    private static ResourceNotFoundException notFound(String what, UUID id) { return new ResourceNotFoundException(what, id.toString()); }
}
