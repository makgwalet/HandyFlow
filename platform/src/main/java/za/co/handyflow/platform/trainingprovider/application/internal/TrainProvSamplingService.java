package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.SamplingRules;
import za.co.handyflow.platform.trainingprovider.dto.SamplingDtos.*;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Moderation sampling. A rule says where it applies; its versions hold the settings and never change. A draw takes the eligible decided
 * attempts in a window, picks some by the rule version in force, and keeps the whole population with the reason for each pick. The
 * moderation itself is still recorded on each attempt in the moderation module. This is the provider's own sampling and is never described as a
 * regulatory requirement. Nothing is deleted.
 */
@Service
@RequiredArgsConstructor
public class TrainProvSamplingService {

    private static final Set<String> SCOPES = Set.of("ALL", "PROGRAMME", "ASSESSMENT_TYPE", "COHORT");
    private static final Set<String> TYPES = Set.of("KNOWLEDGE", "PRACTICAL", "WORKPLACE", "INTEGRATED", "PORTFOLIO");
    private static final Set<String> DECISIONS = Set.of("RESULTS_ACCEPTED", "SAMPLE_EXTENDED", "FURTHER_ACTION_REQUIRED");

    private final TrainProvSamplingRuleRepository rules;
    private final TrainProvSamplingRuleVersionRepository versions;
    private final TrainProvSamplingDrawRepository draws;
    private final TrainProvSamplingDrawItemRepository items;
    private final TrainProvSamplingActionRepository actions;
    private final TrainProvAssessmentAttemptRepository attempts;
    private final TrainProvAssessmentDecisionRepository decisions;
    private final TrainProvAssessmentInstrumentRepository instruments;
    private final TrainProvAssessmentModerationRepository moderations;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvCohortRepository cohorts;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvLearnerRepository learners;
    private final TrainProvPractitionerRepository practitioners;
    private final TrainProvNumberGenerator numbers;

    // ---- rules ----

    public List<RuleView> listRules(TenantId t) {
        List<TrainProvSamplingRule> all = rules.findAllForTenant(t);
        Map<UUID, List<TrainProvSamplingRuleVersion>> byRule = all.isEmpty() ? Map.of()
                : versions.forRules(t, all.stream().map(TrainProvSamplingRule::getId).toList()).stream().collect(Collectors.groupingBy(TrainProvSamplingRuleVersion::getRuleId));
        return all.stream().<RuleView>map(r -> ruleView(t, r, byRule.getOrDefault(r.getId(), List.of()))).toList();
    }

    @Transactional
    public RuleView createRule(TenantId t, RuleRequest r, UUID by) {
        String name = r.name() == null ? "" : r.name().trim();
        if (name.length() < 2) {
            throw bad("Give the rule a name.");
        }
        if (r.scopeType() == null || !SCOPES.contains(r.scopeType())) {
            throw bad("Choose where the rule applies.");
        }
        UUID programmeId = null;
        UUID cohortId = null;
        String type = null;
        switch (r.scopeType()) {
            case "PROGRAMME" -> {
                programmeId = r.programmeId();
                if (programmeId == null || programmes.findForTenant(t, programmeId).isEmpty()) {
                    throw bad("Choose a programme.");
                }
            }
            case "COHORT" -> {
                cohortId = r.cohortId();
                if (cohortId == null || cohorts.findForTenant(t, cohortId).isEmpty()) {
                    throw bad("Choose a cohort.");
                }
            }
            case "ASSESSMENT_TYPE" -> {
                type = r.assessmentType();
                if (type == null || !TYPES.contains(type)) {
                    throw bad("Choose an assessment type.");
                }
            }
            default -> {
            }
        }
        if (rules.findLiveForScope(t, r.scopeType(), programmeId, cohortId, type).isPresent()) {
            throw new HandyFlowException("There is already a live rule for that scope. Add a version to it instead.", HttpStatus.CONFLICT, "TP_SAMPLING_STATE");
        }
        SamplingRules.Settings s = settings(r.settings());
        String problem = SamplingRules.settingsProblem(s);
        if (problem != null) {
            throw bad(problem);
        }
        rationale(r.settings().rationale());
        TrainProvSamplingRule rule = TrainProvSamplingRule.create(t, numbers.nextSamplingRuleNumber(t), name, r.scopeType(), programmeId, cohortId, type, by);
        rules.save(rule);
        versions.save(version(t, rule.getId(), 1, r.settings(), by));
        return ruleView(t, rule, versions.forRule(t, rule.getId()));
    }

    /** A change to the settings is a new version. Earlier versions, and the draws made under them, are untouched. */
    @Transactional
    public RuleView addVersion(TenantId t, UUID ruleId, SettingsRequest r, UUID by, LocalDate today) {
        TrainProvSamplingRule rule = rule(t, ruleId);
        if (!rule.isActive()) {
            throw new HandyFlowException("This rule is retired.", HttpStatus.CONFLICT, "TP_SAMPLING_STATE");
        }
        String problem = SamplingRules.settingsProblem(settings(r));
        if (problem != null) {
            throw bad(problem);
        }
        rationale(r.rationale());
        List<TrainProvSamplingRuleVersion> existing = versions.forRule(t, ruleId);
        if (!existing.isEmpty() && !r.effectiveFrom().isAfter(existing.get(0).getEffectiveFrom())) {
            throw new HandyFlowException("A new version must start after the latest one (" + existing.get(0).getEffectiveFrom() + ").", HttpStatus.CONFLICT, "TP_SAMPLING_STATE");
        }
        versions.save(version(t, ruleId, existing.size() + 1, r, by));
        return ruleView(t, rule, versions.forRule(t, ruleId));
    }

    @Transactional
    public RuleView rename(TenantId t, UUID ruleId, String name) {
        TrainProvSamplingRule rule = rule(t, ruleId);
        if (name == null || name.trim().length() < 2) {
            throw bad("Give the rule a name.");
        }
        rule.rename(name);
        rules.save(rule);
        return ruleView(t, rule, versions.forRule(t, ruleId));
    }

    @Transactional
    public RuleView retire(TenantId t, UUID ruleId, String reason, UUID by) {
        TrainProvSamplingRule rule = rule(t, ruleId);
        guard(() -> { rule.retire(reason, by); return null; });
        rules.save(rule);
        return ruleView(t, rule, versions.forRule(t, ruleId));
    }

    // ---- draws ----

    public List<DrawRow> listDraws(TenantId t, String status) {
        return draws.search(t, status == null || status.isBlank() ? null : status.trim()).stream().<DrawRow>map(d -> {
            TrainProvSamplingRule r = rules.findForTenant(t, d.getRuleId()).orElse(null);
            TrainProvSamplingRuleVersion v = versions.findForTenant(t, d.getRuleVersionId()).orElse(null);
            return new DrawRow(d.getId(), d.getDrawNo(), r == null ? null : r.getRuleNo(), r == null ? null : r.getName(), v == null ? 0 : v.getVersionNo(), d.getDrawnOn(),
                    d.getDecidedFrom(), d.getDecidedTo(), d.getEligibleCount(), d.getRequiredCount(), d.getSelectedCount(), d.getStatus(), d.getDecision());
        }).toList();
    }

    public DrawView getDraw(TenantId t, UUID id) {
        return drawView(t, draw(t, id));
    }

    @Transactional
    public DrawView draw(TenantId t, DrawRequest r, UUID by, LocalDate today) {
        TrainProvSamplingRule rule = rule(t, r.ruleId());
        if (!rule.isActive()) {
            throw new HandyFlowException("This rule is retired.", HttpStatus.CONFLICT, "TP_SAMPLING_STATE");
        }
        if (r.decidedFrom().isAfter(r.decidedTo()) || r.decidedTo().isAfter(today)) {
            throw bad("Choose a window that ends today or earlier, and starts on or before its end.");
        }
        TrainProvSamplingRuleVersion v = versions.inForceOn(t, rule.getId(), today).stream().findFirst()
                .orElseThrow(() -> new HandyFlowException("This rule has no version in force yet.", HttpStatus.CONFLICT, "TP_SAMPLING_STATE"));
        SamplingRules.Settings s = settings(v);
        List<TrainProvAssessmentAttempt> pop = draws.population(t, r.decidedFrom(), r.decidedTo(), rule.getProgrammeId(), rule.getCohortId(), rule.getAssessmentType());
        Map<UUID, TrainProvAssessmentAttempt> byId = pop.stream().collect(Collectors.toMap(TrainProvAssessmentAttempt::getId, a -> a));
        Map<UUID, String> targeted = new LinkedHashMap<>();
        if (r.targeted() != null) {
            for (TargetRequest tr : r.targeted()) {
                String problem = SamplingRules.noteProblem(tr.note());
                if (problem != null) {
                    throw bad(problem);
                }
                if (!byId.containsKey(tr.attemptId())) {
                    throw bad("A targeted record is not in the eligible population for this draw.");
                }
                targeted.put(tr.attemptId(), tr.note().trim());
            }
        }
        Map<UUID, int[]> assessorHistory = new HashMap<>();
        List<SamplingRules.Candidate> candidates = new ArrayList<>();
        Map<UUID, String> factors = new HashMap<>();
        for (TrainProvAssessmentAttempt a : pop) {
            int[] h = assessorHistory.computeIfAbsent(a.getAssessorId(), id -> new int[]{(int) draws.moderationsFor(t, id), (int) draws.queriesFor(t, id)});
            int score = 0;
            List<String> why = new ArrayList<>();
            if (h[1] > 0) {
                score += 2;
                why.add("assessor has had a moderation query");
            }
            if (h[0] == 0) {
                score += 1;
                why.add("assessor has not been moderated before");
            }
            if (a.getAttemptSeq() > 1) {
                score += 1;
                why.add("repeat attempt");
            }
            candidates.add(new SamplingRules.Candidate(a.getId(), a.getAssessorId(), score));
            factors.put(a.getId(), String.join("; ", why));
        }
        List<Boolean> recent = s.extraLookback() == null ? List.of()
                : draws.recentQueries(t, rule.getProgrammeId(), rule.getCohortId(), rule.getAssessmentType(), PageRequest.of(0, s.extraLookback()));
        boolean extra = SamplingRules.extraTriggered(s, recent);
        long seed = r.seed() != null ? r.seed() : new SecureRandom().nextLong();
        SamplingRules.Outcome o = SamplingRules.select(s, candidates, targeted, extra, seed);
        Map<UUID, SamplingRules.Pick> picked = o.picks().stream().collect(Collectors.toMap(SamplingRules.Pick::attemptId, p -> p));
        TrainProvSamplingDraw d = TrainProvSamplingDraw.create(t, numbers.nextSamplingDrawNumber(t), rule.getId(), v.getId(), snapshot(rule, v, extra), today, r.decidedFrom(),
                r.decidedTo(), rule.getProgrammeId(), rule.getCohortId(), rule.getAssessmentType(), seed, o.eligible(), o.required() + o.extraRequired(), o.picks().size(), by);
        draws.save(d);
        for (TrainProvAssessmentAttempt a : pop) {
            SamplingRules.Pick p = picked.get(a.getId());
            items.save(TrainProvSamplingDrawItem.create(t, d.getId(), a.getId(), a.getAssessorId(), riskOf(candidates, a.getId()), factors.get(a.getId()), p != null,
                    p == null ? null : p.reason(), p == null ? null : p.targetNote()));
        }
        return drawView(t, d);
    }

    /** Closes a draw once every picked record has been moderated and any queries are answered. A second person confirms, or the same one says so in writing. */
    @Transactional
    public DrawView close(TenantId t, UUID id, CloseRequest r, UUID by, LocalDate today) {
        TrainProvSamplingDraw d = draw(t, id);
        if (r.decision() == null || !DECISIONS.contains(r.decision())) {
            throw bad("Choose a decision.");
        }
        if (d.getDrawnBy().equals(by) && !r.confirmAlone()) {
            throw new HandyFlowException("A second person should close this draw, or confirm in writing that you are closing it yourself.", HttpStatus.CONFLICT, "TP_SAMPLING_STATE");
        }
        List<TrainProvSamplingDrawItem> picked = items.forDraw(t, id).stream().filter(TrainProvSamplingDrawItem::isSelected).toList();
        int moderated = 0;
        int confirmed = 0;
        int queried = 0;
        int openQueries = 0;
        for (TrainProvSamplingDrawItem i : picked) {
            Optional<TrainProvAssessmentModeration> m = moderations.findForAttempt(t, i.getAttemptId());
            if (m.isPresent()) {
                moderated++;
                if ("QUERIED".equals(m.get().getOutcome())) {
                    queried++;
                    if (!m.get().isResolved()) {
                        openQueries++;
                    }
                } else {
                    confirmed++;
                }
            }
        }
        String problem = SamplingRules.closeProblem(picked.size(), moderated, r.note());
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_SAMPLING_STATE");
        }
        if ("RESULTS_ACCEPTED".equals(r.decision()) && openQueries > 0) {
            throw new HandyFlowException(openQueries + " moderation query(ies) are not answered yet.", HttpStatus.CONFLICT, "TP_SAMPLING_STATE");
        }
        if ("FURTHER_ACTION_REQUIRED".equals(r.decision()) && actions.forDraw(t, id).isEmpty()) {
            throw new HandyFlowException("Record at least one corrective action before closing with further action required.", HttpStatus.CONFLICT, "TP_SAMPLING_STATE");
        }
        String summary = picked.size() + " picked, " + moderated + " moderated, " + confirmed + " confirmed, " + queried + " queried (closed " + today + ")";
        guard(() -> { d.close(r.decision(), r.note(), summary, by); return null; });
        draws.save(d);
        return drawView(t, d);
    }

    @Transactional
    public DrawView voidDraw(TenantId t, UUID id, String reason, UUID by) {
        TrainProvSamplingDraw d = draw(t, id);
        if (items.forDraw(t, id).stream().filter(TrainProvSamplingDrawItem::isSelected)
                .anyMatch(i -> moderations.findForAttempt(t, i.getAttemptId()).isPresent())) {
            throw new HandyFlowException("A picked record has already been moderated, so this draw cannot be voided. Close it instead.", HttpStatus.CONFLICT, "TP_SAMPLING_STATE");
        }
        guard(() -> { d.voidDraw(reason, by); return null; });
        draws.save(d);
        return drawView(t, d);
    }

    // ---- corrective actions ----

    @Transactional
    public DrawView addAction(TenantId t, UUID drawId, ActionRequest r, UUID by) {
        TrainProvSamplingDraw d = draw(t, drawId);
        if (d.getStatus().equals(TrainProvSamplingDraw.VOID)) {
            throw new HandyFlowException("This draw was voided.", HttpStatus.CONFLICT, "TP_SAMPLING_STATE");
        }
        if (r.description() == null || r.description().trim().length() < 10 || r.ownerName() == null || r.ownerName().trim().length() < 2) {
            throw bad("Describe the action in at least 10 characters and say who owns it.");
        }
        if (r.itemId() != null && !items.findForTenant(t, r.itemId()).filter(i -> i.getDrawId().equals(drawId)).isPresent()) {
            throw bad("That record is not part of this draw.");
        }
        actions.save(TrainProvSamplingAction.create(t, drawId, r.itemId(), r.description(), r.ownerName(), r.dueOn(), by));
        return drawView(t, d);
    }

    @Transactional
    public DrawView closeAction(TenantId t, UUID actionId, ActionCloseRequest r, UUID by, LocalDate today) {
        TrainProvSamplingAction a = actions.findForTenant(t, actionId).orElseThrow(() -> new ResourceNotFoundException("TrainProvSamplingAction", actionId.toString()));
        guard(() -> { a.close(r.done(), today, r.note(), by); return null; });
        actions.save(a);
        return drawView(t, draw(t, a.getDrawId()));
    }

    // ---- helpers ----

    private static int riskOf(List<SamplingRules.Candidate> c, UUID attemptId) {
        return c.stream().filter(x -> x.attemptId().equals(attemptId)).mapToInt(SamplingRules.Candidate::riskScore).findFirst().orElse(0);
    }

    private static SamplingRules.Settings settings(SettingsRequest r) {
        return new SamplingRules.Settings(r.basis(), r.percentage(), r.minCount(), r.maxCount(), r.selectionMethod(), r.extraTriggerQueries(), r.extraLookback(), r.extraPercentage());
    }

    private static SamplingRules.Settings settings(TrainProvSamplingRuleVersion v) {
        return new SamplingRules.Settings(v.getBasis(), v.getPercentage(), v.getMinCount(), v.getMaxCount(), v.getSelectionMethod(), v.getExtraTriggerQueries(),
                v.getExtraLookback(), v.getExtraPercentage());
    }

    private static void rationale(String text) {
        if (text == null || text.trim().length() < 10) {
            throw bad("Say why the rule is set this way, in at least 10 characters.");
        }
    }

    private TrainProvSamplingRuleVersion version(TenantId t, UUID ruleId, int no, SettingsRequest r, UUID by) {
        return TrainProvSamplingRuleVersion.create(t, ruleId, no, r.effectiveFrom(), r.basis(), r.percentage(), r.minCount(), r.maxCount(), r.selectionMethod(), r.riskFactors(),
                r.extraTriggerQueries(), r.extraLookback(), r.extraPercentage(), r.rationale(), by);
    }

    /** A readable copy of the settings kept on the draw, so it can be read later without the rule. */
    private static String snapshot(TrainProvSamplingRule rule, TrainProvSamplingRuleVersion v, boolean extraTriggered) {
        StringBuilder sb = new StringBuilder();
        sb.append(rule.getRuleNo()).append(" ").append(rule.getName()).append(", version ").append(v.getVersionNo()).append(" (from ").append(v.getEffectiveFrom()).append("): ");
        sb.append("ALL".equals(v.getBasis()) ? "every eligible record" : v.getPercentage().stripTrailingZeros().toPlainString() + "% of eligible records");
        if (v.getMinCount() != null) {
            sb.append(", at least ").append(v.getMinCount());
        }
        if (v.getMaxCount() != null) {
            sb.append(", at most ").append(v.getMaxCount());
        }
        sb.append(", ").append("RANDOM".equals(v.getSelectionMethod()) ? "random" : "risk-based (" + v.getRiskFactors() + ")").append(" selection");
        if (v.getExtraTriggerQueries() != null) {
            sb.append("; extra ").append(v.getExtraPercentage().stripTrailingZeros().toPlainString()).append("% when ").append(v.getExtraTriggerQueries())
                    .append(" of the last ").append(v.getExtraLookback()).append(" moderations raised a query (").append(extraTriggered ? "triggered" : "not triggered").append(")");
        }
        sb.append(". This is the provider's own sampling rule, not a regulatory requirement.");
        return sb.toString();
    }

    private RuleView ruleView(TenantId t, TrainProvSamplingRule r, List<TrainProvSamplingRuleVersion> vs) {
        List<VersionView> views = vs.stream().sorted(Comparator.comparingInt(TrainProvSamplingRuleVersion::getVersionNo).reversed()).<VersionView>map(this::versionView).toList();
        return new RuleView(r.getId(), r.getRuleNo(), r.getName(), r.getScopeType(), r.getProgrammeId(),
                r.getProgrammeId() == null ? null : programmes.findForTenant(t, r.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse(null), r.getCohortId(),
                r.getCohortId() == null ? null : cohorts.findForTenant(t, r.getCohortId()).map(TrainProvCohort::getName).orElse(null), r.getAssessmentType(), r.getStatus(),
                r.getRetiredReason(), views.isEmpty() ? null : views.get(0), views);
    }

    private VersionView versionView(TrainProvSamplingRuleVersion v) {
        return new VersionView(v.getId(), v.getVersionNo(), v.getEffectiveFrom(), v.getBasis(), v.getPercentage(), v.getMinCount(), v.getMaxCount(), v.getSelectionMethod(),
                v.getRiskFactors(), v.getExtraTriggerQueries(), v.getExtraLookback(), v.getExtraPercentage(), v.getRationale(), v.getCreatedAt());
    }

    private DrawView drawView(TenantId t, TrainProvSamplingDraw d) {
        TrainProvSamplingRule r = rules.findForTenant(t, d.getRuleId()).orElse(null);
        TrainProvSamplingRuleVersion v = versions.findForTenant(t, d.getRuleVersionId()).orElse(null);
        List<ItemView> itemViews = items.forDraw(t, d.getId()).stream().sorted(Comparator.comparing(TrainProvSamplingDrawItem::isSelected).reversed()
                .thenComparing(Comparator.comparingInt(TrainProvSamplingDrawItem::getRiskScore).reversed())).<ItemView>map(i -> itemView(t, i)).toList();
        int moderated = (int) itemViews.stream().filter(i -> i.selected() && i.moderationNo() != null).count();
        List<ActionView> actionViews = actions.forDraw(t, d.getId()).stream().<ActionView>map(a -> new ActionView(a.getId(), a.getItemId(), a.getDescription(), a.getOwnerName(),
                a.getDueOn(), a.getStatus(), a.getClosedOn(), a.getClosedNote())).toList();
        return new DrawView(d.getId(), d.getDrawNo(), d.getRuleId(), r == null ? null : r.getRuleNo(), r == null ? null : r.getName(), v == null ? 0 : v.getVersionNo(),
                d.getRuleSnapshot(), d.getDrawnOn(), d.getDecidedFrom(), d.getDecidedTo(), d.getSeed(), d.getEligibleCount(), d.getRequiredCount(), d.getSelectedCount(),
                d.getStatus(), d.getDrawnBy(), d.getDecision(), d.getDecisionNote(), d.getSummary(), d.getVoidReason(), moderated, itemViews, actionViews);
    }

    private ItemView itemView(TenantId t, TrainProvSamplingDrawItem i) {
        TrainProvAssessmentAttempt a = attempts.findForTenant(t, i.getAttemptId()).orElse(null);
        TrainProvAssessmentModeration m = moderations.findForAttempt(t, i.getAttemptId()).orElse(null);
        return new ItemView(i.getId(), i.getAttemptId(), a == null ? null : a.getAttemptNo(), a == null ? null : instruments.findForTenant(t, a.getInstrumentId()).map(TrainProvAssessmentInstrument::getTitle).orElse(null),
                a == null ? null : enrolments.findForTenant(t, a.getEnrolmentId()).flatMap(e -> learners.findForTenant(t, e.getLearnerId())).map(l -> l.getFirstNames() + " " + l.getSurname()).orElse(null),
                i.getAssessorId(), practitioners.findForTenant(t, i.getAssessorId()).map(p -> p.getFirstNames() + " " + p.getSurname()).orElse(null),
                decisions.findForAttempt(t, i.getAttemptId()).map(TrainProvAssessmentDecision::getDecision).orElse(null), i.getRiskScore(), i.getRiskFactors(), i.isSelected(),
                i.getSelectedReason(), i.getTargetNote(), m == null ? null : m.getModerationNo(), m == null ? null : m.getOutcome(), m != null && (m.isResolved() || !"QUERIED".equals(m.getOutcome())));
    }

    private TrainProvSamplingRule rule(TenantId t, UUID id) {
        return rules.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvSamplingRule", id.toString()));
    }

    private TrainProvSamplingDraw draw(TenantId t, UUID id) {
        return draws.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvSamplingDraw", id.toString()));
    }

    private static HandyFlowException bad(String message) {
        return new HandyFlowException(message, HttpStatus.BAD_REQUEST, "TP_SAMPLING_INVALID");
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_SAMPLING_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_SAMPLING_INVALID");
        }
    }
}
