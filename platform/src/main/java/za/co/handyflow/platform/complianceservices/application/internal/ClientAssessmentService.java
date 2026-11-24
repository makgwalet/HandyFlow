package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.businessreadiness.DocumentFact;
import za.co.handyflow.platform.businessreadiness.ReadinessItem;
import za.co.handyflow.platform.businessreadiness.RequirementReadinessEvaluator;
import za.co.handyflow.platform.businessreadiness.RequirementRule;
import za.co.handyflow.platform.businessreadiness.RequirementToEvaluate;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.AssessmentDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Judges every obligation assigned to a client on one date. Evidence is checked by the shared readiness evaluator,
 * but only a person can say a control is in place. Completing freezes the result, the weights and the date.
 */
@Service
@RequiredArgsConstructor
public class ClientAssessmentService {

    private final ComplianceClientRepository clients;
    private final ComplianceClientObligationRepository assignments;
    private final ComplianceObligationRepository obligations;
    private final ComplianceObligationVersionRepository versions;
    private final ComplianceObligationEvidenceRepository obligationEvidence;
    private final ClientComplianceDocumentRepository documents;
    private final ComplianceAssessmentRepository assessments;
    private final ComplianceAssessmentItemRepository items;
    private final ComplianceAssessmentEvidenceRepository evidence;
    private final ComplianceAssessmentWeightsRepository weightsRepo;

    // ---- weights ----

    @Transactional(readOnly = true)
    public WeightsView weights(TenantId tenantId) {
        return weightsRepo.findForTenant(tenantId).map(w -> view(w.asWeights(), false)).orElseGet(() -> view(AssessmentRules.Weights.defaults(), true));
    }

    /** Changes only assessments started afterwards. Started and completed ones keep the weights they were scored with. */
    @Transactional
    public WeightsView setWeights(TenantId tenantId, WeightsRequest r, UUID by) {
        AssessmentRules.Weights w;
        try {
            w = new AssessmentRules.Weights(r.low(), r.medium(), r.high(), r.critical());
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "ASSESSMENT_WEIGHTS_INVALID");
        }
        ComplianceAssessmentWeights row = weightsRepo.findForTenant(tenantId).orElseGet(() -> ComplianceAssessmentWeights.forTenant(tenantId));
        row.set(w, by);
        weightsRepo.save(row);
        return view(w, false);
    }

    // ---- assessments ----

    @Transactional(readOnly = true)
    public List<AssessmentSummary> list(TenantId tenantId, UUID clientId) {
        requireClient(tenantId, clientId);
        return assessments.findForClient(tenantId, clientId).stream().map(a -> new AssessmentSummary(a.getId(), a.getStatus(), a.getAsOf(),
                a.getScorePercent(), a.getScoredCount(), a.getUnscoredCount(), a.getStartedAt(), a.getCompletedAt())).toList();
    }

    @Transactional
    public AssessmentView start(TenantId tenantId, UUID clientId, UUID by, LocalDate today) {
        requireClient(tenantId, clientId);
        List<ComplianceClientObligation> assigned = assignments.findForClient(tenantId, clientId);
        if (assigned.isEmpty()) {
            throw new HandyFlowException("No obligations are assigned to this client yet. Update the suggestions on the Obligations tab first",
                    HttpStatus.BAD_REQUEST, "ASSESSMENT_NO_OBLIGATIONS");
        }
        if (assessments.findDraft(tenantId, clientId).isPresent()) {
            throw new HandyFlowException("An assessment is already in progress for this client. Complete it first", HttpStatus.CONFLICT, "ASSESSMENT_IN_PROGRESS");
        }
        AssessmentRules.Weights w = weightsRepo.findForTenant(tenantId).map(ComplianceAssessmentWeights::asWeights).orElseGet(AssessmentRules.Weights::defaults);
        ComplianceAssessment a = assessments.save(ComplianceAssessment.start(tenantId, clientId, today, w, by));

        Map<UUID, ComplianceObligation> obById = obligations.findAllForTenant(tenantId).stream().collect(Collectors.toMap(ComplianceObligation::getId, o -> o));
        Map<UUID, ComplianceObligationVersion> vById = versions.findAllForTenant(tenantId).stream().collect(Collectors.toMap(ComplianceObligationVersion::getId, v -> v));
        for (ComplianceClientObligation c : assigned) {
            ComplianceObligation o = obById.get(c.getObligationId());
            ComplianceObligationVersion v = vById.get(c.getVersionId());
            ComplianceAssessmentItem item = ComplianceAssessmentItem.of(tenantId, a.getId(), c.getObligationId(), c.getVersionId(), c.getVersionNumber(),
                    o == null ? "?" : o.getCode(), v == null ? null : v.getName(), v == null ? null : v.getRiskLevel(), c.effectiveState(), "NOT_REQUIRED");
            items.save(item);
        }
        checkEvidence(tenantId, clientId, a, today);
        return build(tenantId, a);
    }

    /** Checks the documents again, for example after one was uploaded or verified. People's judgements stay. */
    @Transactional
    public AssessmentView recheck(TenantId tenantId, UUID clientId, UUID assessmentId, LocalDate today) {
        requireClient(tenantId, clientId);
        ComplianceAssessment a = find(tenantId, clientId, assessmentId);
        requireDraft(a);
        checkEvidence(tenantId, clientId, a, today);
        return build(tenantId, a);
    }

    @Transactional
    public AssessmentView judge(TenantId tenantId, UUID clientId, UUID assessmentId, UUID obligationId, JudgeRequest r, UUID by) {
        requireClient(tenantId, clientId);
        ComplianceAssessment a = find(tenantId, clientId, assessmentId);
        requireDraft(a);
        ComplianceAssessmentItem item = items.findOne(tenantId, assessmentId, obligationId)
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceAssessmentItem", obligationId.toString()));
        try {
            item.judge(r.control(), r.workflow(), r.note(), by);
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "ASSESSMENT_ITEM_INVALID");
        }
        items.save(item);
        return build(tenantId, a);
    }

    @Transactional
    public AssessmentView complete(TenantId tenantId, UUID clientId, UUID assessmentId, CompleteRequest r, UUID by) {
        requireClient(tenantId, clientId);
        ComplianceAssessment a = find(tenantId, clientId, assessmentId);
        requireDraft(a);
        List<ComplianceAssessmentItem> list = items.findForAssessment(tenantId, assessmentId);
        List<String> blockers = AssessmentRules.completionBlockers(list.stream()
                .map(i -> new AssessmentRules.DraftItem(i.getCode(), i.getApplicability(), i.getControl(), i.getNote())).toList());
        if (!blockers.isEmpty()) {
            throw new HandyFlowException("Judge these obligations, or say why they cannot be judged yet: " + String.join(", ", blockers),
                    HttpStatus.BAD_REQUEST, "ASSESSMENT_INCOMPLETE");
        }
        try {
            a.complete(scoreOf(a, list), r == null ? null : r.summaryNote(), by);
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "ASSESSMENT_INVALID");
        }
        assessments.save(a);
        return build(tenantId, a);
    }

    @Transactional(readOnly = true)
    public AssessmentView get(TenantId tenantId, UUID clientId, UUID assessmentId) {
        requireClient(tenantId, clientId);
        return build(tenantId, find(tenantId, clientId, assessmentId));
    }

    // ---- helpers ----

    private void checkEvidence(TenantId tenantId, UUID clientId, ComplianceAssessment a, LocalDate today) {
        List<ComplianceAssessmentItem> list = items.findForAssessment(tenantId, a.getId());
        Map<UUID, List<ComplianceObligationEvidence>> required = list.isEmpty() ? Map.of()
                : obligationEvidence.findForVersions(tenantId, list.stream().map(ComplianceAssessmentItem::getVersionId).distinct().toList()).stream()
                        .collect(Collectors.groupingBy(ComplianceObligationEvidence::getVersionId));
        List<DocumentFact> facts = documents.findByClient(tenantId, clientId).stream().filter(ClientComplianceDocument::isCurrent)
                .map(d -> new DocumentFact(d.getDocumentType(), d.getExpiryDate(), d.isVerified())).toList();

        for (ComplianceAssessmentItem item : list) {
            List<ComplianceObligationEvidence> rows = required.getOrDefault(item.getVersionId(), List.of());
            List<RequirementToEvaluate> toEvaluate = rows.stream()
                    .map(e -> new RequirementToEvaluate(null, e.getDocumentType(), null, new RequirementRule(null, null, e.getDocumentType()), false)).toList();
            List<ReadinessItem> results = toEvaluate.isEmpty() ? List.of()
                    : RequirementReadinessEvaluator.evaluate(today, a.getAsOf(), "TODAY", toEvaluate, List.of(), facts).items();

            evidence.deleteForItem(item.getId());
            List<AssessmentRules.EvidenceCheck> checks = new ArrayList<>();
            for (int n = 0; n < rows.size(); n++) {
                ComplianceObligationEvidence row = rows.get(n);
                ReadinessItem r = results.get(n);
                String result = r.result().name();
                checks.add(new AssessmentRules.EvidenceCheck(row.getDocumentType(), row.isMandatory(), result));
                evidence.save(ComplianceAssessmentEvidence.of(tenantId, item.getId(), row.getDocumentType(), row.isMandatory(), result, r.detail(), r.expiresOn()));
            }
            item.recheck(AssessmentRules.evidenceStatus(checks));
            items.save(item);
        }
    }

    private AssessmentRules.Score scoreOf(ComplianceAssessment a, List<ComplianceAssessmentItem> list) {
        return AssessmentRules.score(list.stream().map(i -> new AssessmentRules.Line(i.getCode(), i.getRiskLevel(), i.getStatus())).toList(), a.weights());
    }

    private AssessmentView build(TenantId tenantId, ComplianceAssessment a) {
        List<ComplianceAssessmentItem> list = items.findForAssessment(tenantId, a.getId());
        Map<UUID, List<ComplianceAssessmentEvidence>> ev = list.isEmpty() ? Map.of()
                : evidence.findForItems(tenantId, list.stream().map(ComplianceAssessmentItem::getId).toList()).stream()
                        .collect(Collectors.groupingBy(ComplianceAssessmentEvidence::getItemId));
        List<ItemView> views = list.stream().map(i -> new ItemView(i.getObligationId(), i.getCode(), i.getName(), i.getVersionNumber(), i.getRiskLevel(),
                i.getApplicability(), i.getEvidenceStatus(), i.getControl(), i.getWorkflow(), i.getStatus(), i.displayStatus(), i.getNote(), i.getAssessedBy(),
                i.getAssessedAt(), ev.getOrDefault(i.getId(), List.of()).stream()
                        .map(e -> new EvidenceView(e.getDocumentType(), e.isMandatory(), e.getResult(), e.getDetail(), e.getExpiresOn())).toList())).toList();

        ScoreView score;
        List<String> blockers = List.of();
        if (a.isDraft()) {
            AssessmentRules.Score s = scoreOf(a, list);
            score = new ScoreView(s.percent(), s.scored(), s.unscored(), true);
            blockers = AssessmentRules.completionBlockers(list.stream()
                    .map(i -> new AssessmentRules.DraftItem(i.getCode(), i.getApplicability(), i.getControl(), i.getNote())).toList());
        } else {
            score = new ScoreView(a.getScorePercent(), a.getScoredCount() == null ? 0 : a.getScoredCount(), a.getUnscoredCount() == null ? 0 : a.getUnscoredCount(), false);
        }

        ComplianceAssessment previous = assessments.findForClient(tenantId, a.getClientId()).stream()
                .filter(x -> !x.isDraft() && !x.getId().equals(a.getId()) && (a.isDraft() || x.getCompletedAt().isBefore(a.getCompletedAt())))
                .max(Comparator.comparing(ComplianceAssessment::getCompletedAt)).orElse(null);
        List<ChangeView> changes = List.of();
        if (previous != null) {
            List<ComplianceAssessmentItem> before = items.findForAssessment(tenantId, previous.getId());
            changes = AssessmentRules.changes(snapshots(tenantId, before), snapshots(tenantId, list)).stream()
                    .map(c -> new ChangeView(c.code(), c.from(), c.to(), c.reason())).toList();
        }
        AssessmentRules.Weights w = a.weights();
        return new AssessmentView(a.getId(), a.getClientId(), a.getStatus(), a.getAsOf(), view(w, false), score, a.getSummaryNote(), a.getStartedBy(),
                a.getStartedAt(), a.getCompletedBy(), a.getCompletedAt(), views, blockers, previous == null ? null : previous.getId(), changes);
    }

    private Map<String, AssessmentRules.Snapshot> snapshots(TenantId tenantId, List<ComplianceAssessmentItem> list) {
        Map<UUID, List<ComplianceAssessmentEvidence>> ev = list.isEmpty() ? Map.of()
                : evidence.findForItems(tenantId, list.stream().map(ComplianceAssessmentItem::getId).toList()).stream()
                        .collect(Collectors.groupingBy(ComplianceAssessmentEvidence::getItemId));
        Map<String, AssessmentRules.Snapshot> out = new LinkedHashMap<>();
        for (ComplianceAssessmentItem i : list) {
            String reason = i.getNote();
            if (reason == null || reason.isBlank()) {
                reason = ev.getOrDefault(i.getId(), List.of()).stream().filter(e -> e.isMandatory() && !"MET".equals(e.getResult()))
                        .map(ComplianceAssessmentEvidence::getDetail).filter(Objects::nonNull).findFirst().orElse(null);
            }
            out.put(i.getCode(), new AssessmentRules.Snapshot(i.getStatus(), reason));
        }
        return out;
    }

    private static WeightsView view(AssessmentRules.Weights w, boolean isDefault) {
        return new WeightsView(w.low(), w.medium(), w.high(), w.critical(), isDefault);
    }

    private ComplianceClient requireClient(TenantId tenantId, UUID clientId) {
        return clients.findByIdForTenant(tenantId, clientId).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
    }

    private ComplianceAssessment find(TenantId tenantId, UUID clientId, UUID id) {
        return assessments.findOne(tenantId, clientId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceAssessment", id.toString()));
    }

    private static void requireDraft(ComplianceAssessment a) {
        if (!a.isDraft()) throw new HandyFlowException("This assessment is completed and can no longer change", HttpStatus.CONFLICT, "ASSESSMENT_COMPLETED");
    }
}
