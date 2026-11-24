package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides, from a client's profile and an obligation's conditions, whether the obligation applies. A fact nobody
 * has recorded is "unknown", never a quiet yes or no. An obligation with no conditions is never assumed to apply.
 */
public final class ApplicabilityRules {

    private ApplicabilityRules() {}

    public static final String APPLICABLE = "APPLICABLE";
    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String PENDING = "PENDING";
    public static final Set<String> STATES = Set.of(APPLICABLE, NOT_APPLICABLE, PENDING);
    public static final int MIN_REASON = 15;
    public static final int MAX_REVIEW_MONTHS = 24;

    /** What is recorded about the client. Null means not recorded. */
    public record Facts(String entityType, String industry, Integer employeeCount, String turnoverBand, Boolean vatRegistered) {}

    public enum Outcome { MET, UNMET, UNKNOWN }

    public record Check(String field, Outcome outcome, String text, boolean factConfirmed) {}

    public record Evaluation(String state, List<Check> checks, boolean basedOnUnconfirmed) {
        public String reasons() { return String.join("\n", checks.stream().map(Check::text).toList()); }
    }

    /** The profile fact a condition field relies on, as named in the source record. */
    public static String trackedFact(String conditionField) {
        return switch (conditionField) {
            case "ENTITY_TYPE" -> "entityType";
            case "INDUSTRY" -> "industry";
            case "EMPLOYEE_COUNT" -> "employeeCount";
            case "TURNOVER_BAND" -> "turnoverBand";
            default -> "vatNumber";
        };
    }

    public static Evaluation evaluate(List<ObligationRules.Condition> conditions, Facts f, Set<String> confirmedFacts) {
        if (conditions.isEmpty()) {
            return new Evaluation(PENDING, List.of(new Check("-", Outcome.UNKNOWN, "No conditions are set, so an assessor has to decide.", true)), false);
        }
        List<Check> checks = new ArrayList<>();
        for (ObligationRules.Condition c : conditions) checks.add(check(c, f, confirmedFacts.contains(trackedFact(c.field()))));
        boolean unmet = checks.stream().anyMatch(c -> c.outcome() == Outcome.UNMET);
        boolean unknown = checks.stream().anyMatch(c -> c.outcome() == Outcome.UNKNOWN);
        boolean unconfirmed = checks.stream().anyMatch(c -> c.outcome() != Outcome.UNKNOWN && !c.factConfirmed());
        String state = unmet ? NOT_APPLICABLE : unknown ? PENDING : APPLICABLE;
        return new Evaluation(state, checks, unconfirmed);
    }

    private static Check check(ObligationRules.Condition c, Facts f, boolean confirmed) {
        List<String> values = c.value() == null ? List.of() : Arrays.stream(c.value().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        switch (c.field()) {
            case "ENTITY_TYPE", "TURNOVER_BAND" -> {
                String have = c.field().equals("ENTITY_TYPE") ? f.entityType() : f.turnoverBand();
                String name = c.field().equals("ENTITY_TYPE") ? "Entity type" : "Turnover band";
                if (have == null) return new Check(c.field(), Outcome.UNKNOWN, name + " is not recorded.", confirmed);
                boolean met = values.contains(have.toUpperCase(Locale.ROOT));
                return new Check(c.field(), met ? Outcome.MET : Outcome.UNMET, name + " is " + have + (met ? ", which is required." : ", not " + String.join(" or ", values) + "."), confirmed);
            }
            case "INDUSTRY" -> {
                if (f.industry() == null) return new Check(c.field(), Outcome.UNKNOWN, "Industry is not recorded.", confirmed);
                boolean met = values.stream().anyMatch(v -> v.equalsIgnoreCase(f.industry().trim()));
                return new Check(c.field(), met ? Outcome.MET : Outcome.UNMET, "Industry is " + f.industry() + (met ? ", which is required." : ", not " + String.join(" or ", values) + "."), confirmed);
            }
            case "EMPLOYEE_COUNT" -> {
                if (f.employeeCount() == null) return new Check(c.field(), Outcome.UNKNOWN, "Employee count is not recorded.", confirmed);
                int limit = Integer.parseInt(c.value());
                boolean met = c.operator().equals("GTE") ? f.employeeCount() >= limit : f.employeeCount() <= limit;
                return new Check(c.field(), met ? Outcome.MET : Outcome.UNMET, f.employeeCount() + " employees; needs " + (c.operator().equals("GTE") ? "at least " : "at most ") + limit + ".", confirmed);
            }
            default -> {   // VAT_REGISTERED: a VAT number on file means registered; no number is not proof of the opposite
                if (f.vatRegistered() == null) return new Check(c.field(), Outcome.UNKNOWN, "No VAT number is recorded, so VAT registration is unknown.", confirmed);
                boolean want = c.operator().equals("IS_TRUE");
                boolean met = f.vatRegistered() == want;
                return new Check(c.field(), met ? Outcome.MET : Outcome.UNMET, "The client " + (f.vatRegistered() ? "is" : "is not") + " VAT registered.", confirmed);
            }
        }
    }

    // ---- decisions ----

    /** A person's decision needs a reason when it departs from the suggestion, and a review date when it removes an obligation. */
    public static void checkDecision(String state, String suggested, String reason, LocalDate reviewDue, LocalDate today) {
        if (!STATES.contains(state)) throw new IllegalArgumentException("state must be one of " + STATES.stream().sorted().toList());
        boolean differs = !state.equals(suggested);
        String r = reason == null ? "" : reason.trim();
        if (differs && r.length() < MIN_REASON) {
            throw new IllegalArgumentException("This differs from the suggestion (" + suggested + "), so give a reason of at least " + MIN_REASON + " characters");
        }
        if (NOT_APPLICABLE.equals(state)) {
            if (reviewDue == null) throw new IllegalArgumentException("Say when this will be looked at again");
            if (!reviewDue.isAfter(today)) throw new IllegalArgumentException("The review date must be in the future");
            if (reviewDue.isAfter(today.plusMonths(MAX_REVIEW_MONTHS))) throw new IllegalArgumentException("Look at it again within " + MAX_REVIEW_MONTHS + " months");
        }
    }

    /** Removing something the rules say applies, or that is still undecided, needs a second person to agree. */
    public static boolean needsApproval(String state, String suggested) {
        return NOT_APPLICABLE.equals(state) && !NOT_APPLICABLE.equals(suggested);
    }

    /** What the rest of the system should treat the obligation as. An unapproved removal still counts as applicable. */
    public static String effectiveState(String state, String approvalStatus) {
        return NOT_APPLICABLE.equals(state) && "AWAITING".equals(approvalStatus) ? APPLICABLE : state;
    }

    /** Whether a person should look at the decision again: a newer version, or the review date has come. */
    public static boolean reviewNeeded(Integer decidedVersion, int currentVersion, LocalDate reviewDue, LocalDate today) {
        if (decidedVersion != null && decidedVersion < currentVersion) return true;
        return reviewDue != null && !reviewDue.isAfter(today);
    }
}
