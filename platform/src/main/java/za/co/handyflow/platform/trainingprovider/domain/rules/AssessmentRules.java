package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rules for assessment instruments. An instrument may carry a mark scale. It never carries a pass mark: the mark and the competency
 * decision are separate, and the decision is a person's, recorded later with evidence.
 */
public final class AssessmentRules {

    private AssessmentRules() {
    }

    public static final Set<String> TYPES = Set.of("KNOWLEDGE", "PRACTICAL", "WORKPLACE", "INTEGRATED", "PORTFOLIO");
    public static final String DRAFT = "DRAFT";
    public static final String APPROVED = "APPROVED";
    public static final String RETIRED = "RETIRED";

    private static final Map<String, Set<String>> NEXT = Map.of(DRAFT, Set.of(APPROVED, RETIRED), APPROVED, Set.of(RETIRED), RETIRED, Set.of());

    public static Set<String> nextStatuses(String from) {
        return NEXT.getOrDefault(from, Set.of());
    }

    /** Why an instrument's basic shape is wrong, or null. */
    public static String instrumentProblem(String title, String type, BigDecimal maxMark) {
        if (!CohortRules.longEnough(title, 2)) {
            return "Give the instrument a title";
        }
        if (type == null || !TYPES.contains(type)) {
            return "Choose knowledge, practical, workplace, integrated or portfolio";
        }
        if (maxMark != null && maxMark.signum() <= 0) {
            return "The maximum mark must be more than 0, or left empty if the instrument is not marked";
        }
        return null;
    }

    /** Why the module cannot be used for the instrument, or null. A module belongs to one programme version. */
    public static String moduleProblem(UUID moduleVersionId, UUID instrumentVersionId) {
        return moduleVersionId.equals(instrumentVersionId) ? null : "That module belongs to a different version of the programme";
    }

    public static String editProblem(String status) {
        return DRAFT.equals(status) ? null : "Only a draft instrument can be edited. Retire this one and create a replacement";
    }

    /**
     * Why the instrument cannot be approved, or null. A second person approves. If the person approving also wrote it, they must say why in
     * at least 10 characters and the reason is kept.
     */
    public static String approvalProblem(String status, boolean approverIsAuthor, String overrideReason) {
        if (!DRAFT.equals(status)) {
            return "Only a draft instrument can be approved";
        }
        if (approverIsAuthor && !CohortRules.longEnough(overrideReason, CohortRules.MIN_REASON)) {
            return "A second person should approve an instrument they did not write. If no one else can, say why in at least " + CohortRules.MIN_REASON + " characters";
        }
        return null;
    }

    public static String retireProblem(String status, String reason) {
        if (RETIRED.equals(status)) {
            return "This instrument is already retired";
        }
        return CohortRules.reasonProblem(reason);
    }
}
