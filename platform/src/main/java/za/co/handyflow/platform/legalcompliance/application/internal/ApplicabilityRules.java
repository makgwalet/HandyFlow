package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.domain.model.ApplicabilitySuggestion;

import java.util.*;

/**
 * Pure, stateless: turns an organisation profile and a requirement's recorded conditions into a SUGGESTION.
 * <ul>
 *  <li>It never decides. A person records the decision, with a justification.</li>
 *  <li>No recorded conditions = no basis = NEEDS_REVIEW. A requirement is never assumed to apply to everyone.</li>
 *  <li>Every recorded condition is evaluated as PASS, FAIL or UNKNOWN (profile data missing).
 *      Conditions are ANDed: any FAIL gives LIKELY_NOT_APPLICABLE (a definite miss is decisive);
 *      otherwise any UNKNOWN gives NEEDS_REVIEW; otherwise LIKELY_APPLICABLE.</li>
 *  <li>industries: passes when ANY listed industry matches the profile.
 *      activities: passes when ANY listed activity matches the profile.</li>
 *  <li>Matching is case-insensitive. Reasons are returned so the assessor can see why.</li>
 * </ul>
 */
public final class ApplicabilityRules {

    private ApplicabilityRules() {}

    public record Profile(Set<String> industries, Set<String> activities, Integer employeeCount) {
        public Profile {
            industries = upper(industries);
            activities = upper(activities);
        }
    }

    public record Conditions(Integer minEmployees, Set<String> industries, Set<String> activities) {
        public Conditions {
            industries = upper(industries);
            activities = upper(activities);
        }
        public boolean isEmpty() { return minEmployees == null && industries.isEmpty() && activities.isEmpty(); }
    }

    public record Result(ApplicabilitySuggestion suggestion, List<String> reasons) {}

    public static Result suggest(Profile profile, Conditions c) {
        if (c.isEmpty()) {
            return new Result(ApplicabilitySuggestion.NEEDS_REVIEW,
                    List.of("No applicability conditions are recorded for this requirement — it needs a person's judgement"));
        }
        List<String> reasons = new ArrayList<>();
        boolean fail = false, unknown = false;

        if (c.minEmployees() != null) {
            if (profile.employeeCount() == null) {
                unknown = true;
                reasons.add("Applies from " + c.minEmployees() + " employees — your employee count is not recorded");
            } else if (profile.employeeCount() >= c.minEmployees()) {
                reasons.add("Applies from " + c.minEmployees() + " employees — you have " + profile.employeeCount());
            } else {
                fail = true;
                reasons.add("Applies from " + c.minEmployees() + " employees — you have " + profile.employeeCount());
            }
        }
        if (!c.industries().isEmpty()) {
            if (profile.industries().isEmpty()) {
                unknown = true;
                reasons.add("Applies to industries " + c.industries() + " — your industries are not recorded");
            } else if (!Collections.disjoint(c.industries(), profile.industries())) {
                reasons.add("Industry matches " + intersection(c.industries(), profile.industries()));
            } else {
                fail = true;
                reasons.add("Applies to industries " + c.industries() + " — none match yours " + profile.industries());
            }
        }
        if (!c.activities().isEmpty()) {
            if (profile.activities().isEmpty()) {
                unknown = true;
                reasons.add("Applies to activities " + c.activities() + " — your activities are not recorded");
            } else if (!Collections.disjoint(c.activities(), profile.activities())) {
                reasons.add("Activity matches " + intersection(c.activities(), profile.activities()));
            } else {
                fail = true;
                reasons.add("Applies to activities " + c.activities() + " — none match yours " + profile.activities());
            }
        }

        ApplicabilitySuggestion s = fail ? ApplicabilitySuggestion.LIKELY_NOT_APPLICABLE
                : unknown ? ApplicabilitySuggestion.NEEDS_REVIEW
                : ApplicabilitySuggestion.LIKELY_APPLICABLE;
        return new Result(s, reasons);
    }

    private static Set<String> intersection(Set<String> a, Set<String> b) {
        Set<String> out = new TreeSet<>(a);
        out.retainAll(b);
        return out;
    }

    private static Set<String> upper(Set<String> in) {
        if (in == null) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String s : in) if (s != null && !s.isBlank()) out.add(s.trim().toUpperCase());
        return out;
    }
}
