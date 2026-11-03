package za.co.handyflow.platform.documents.domain.model;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Records retention rules, free of Spring and JPA. Which policy applies to an item, when it falls due, and the checks around holds
 * and the two-person disposal rule. Nothing here deletes anything.
 */
public final class RetentionRules {
    private RetentionRules() {}

    /** The approved default until the compliance owner sets a period: 7 years from when the clock starts. */
    public static final int DEFAULT_YEARS = 7;
    public static final String DEFAULT_JURISDICTION = "ZA";
    /** A policy row for this category applies to every category of the module that has no row of its own. */
    public static final String ANY_CATEGORY = "*";
    public static final int MAX_BATCH = 500;
    /** How far ahead "coming due" looks. */
    public static final int UPCOMING_DAYS = 90;

    public enum Source { POLICY, MODULE_POLICY, DEFAULT }

    /** The period that applies to one item, and where it came from. */
    public record Resolved(int years, Source source, UUID policyId, boolean confirmed, boolean personalInfo) {}

    /** The fields of a policy the resolution needs (the entity, or a test double). */
    public interface PolicyView {
        UUID getId(); String getModuleCode(); String getCategory(); String getJurisdiction();
        int getRetainYears(); boolean isConfirmed(); boolean isPersonalInfo();
    }

    /** The most specific policy wins: this category, then the module's '*' row, then the 7-year default. */
    public static Resolved resolve(Collection<? extends PolicyView> policies, String module, String category, String jurisdiction) {
        PolicyView exact = null, any = null;
        for (PolicyView p : policies) {
            if (!p.getModuleCode().equals(module) || !p.getJurisdiction().equals(jurisdiction)) continue;
            if (p.getCategory().equals(category)) exact = p;
            else if (p.getCategory().equals(ANY_CATEGORY)) any = p;
        }
        if (exact != null) return new Resolved(exact.getRetainYears(), Source.POLICY, exact.getId(), exact.isConfirmed(), exact.isPersonalInfo());
        if (any != null) return new Resolved(any.getRetainYears(), Source.MODULE_POLICY, any.getId(), any.isConfirmed(), any.isPersonalInfo());
        return new Resolved(DEFAULT_YEARS, Source.DEFAULT, null, false, false);
    }

    public static LocalDate retainUntil(LocalDate clockStart, int years) {
        return clockStart == null ? null : clockStart.plusYears(years);
    }

    /** Due for disposal review: still active, not held, and its retention date has passed (the date itself is still kept). */
    public static boolean isDue(String status, boolean legalHold, LocalDate retainUntil, LocalDate today) {
        return "ACTIVE".equals(status) && !legalHold && retainUntil != null && retainUntil.isBefore(today);
    }

    /** A disposal, a hold or a rejection always says why. */
    public static String reason(String s, String what) {
        String v = s == null ? "" : s.replaceAll("\\p{Cntrl}", " ").trim();
        if (v.length() < 3) throw new IllegalArgumentException("Give a reason to " + what + ".");
        if (v.length() > 500) throw new IllegalArgumentException("Keep the reason to 500 characters.");
        return v;
    }

    public static String optional(String s, int max) {
        if (s == null) return null;
        String v = s.replaceAll("\\p{Cntrl}", " ").trim();
        if (v.isEmpty()) return null;
        return v.length() > max ? v.substring(0, max) : v;
    }

    /** Module and category codes as stored: upper case letters, digits and underscore ('*' allowed for a category). */
    public static String code(String s, int max, String what) {
        String v = s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
        if (v.equals(ANY_CATEGORY)) return v;
        v = v.replaceAll("[^A-Z0-9_]", "_");
        if (v.isEmpty()) throw new IllegalArgumentException("The " + what + " is required.");
        return v.length() > max ? v.substring(0, max) : v;
    }

    public static String jurisdiction(String s) {
        String v = s == null || s.isBlank() ? DEFAULT_JURISDICTION : s.trim().toUpperCase(Locale.ROOT);
        if (!v.matches("[A-Z]{2}(-[A-Z0-9]{1,3})?")) throw new IllegalArgumentException("Use a country code such as ZA, or ZA-GP for a province.");
        return v;
    }

    public static int years(Integer y) {
        if (y == null || y < 1 || y > 100) throw new IllegalArgumentException("A retention period is between 1 and 100 years.");
        return y;
    }

    /** The proposer may not approve or reject their own proposal. Unknown people never approve. */
    public static boolean sameOrUnknownPerson(UUID proposer, UUID decider) {
        return decider == null || Objects.equals(proposer, decider);
    }
}
