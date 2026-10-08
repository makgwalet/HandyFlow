package za.co.handyflow.platform.clinic.application.internal;

import java.util.Locale;
import java.util.Set;

/** What may be written as family history, lifestyle and social history, and medical aid details. Pure, so it is tested without a database. */
final class HistoryRules {
    static final Set<String> RELATIVES = Set.of("MOTHER", "FATHER", "SIBLING", "GRANDPARENT", "CHILD", "AUNT_UNCLE", "COUSIN", "OTHER");
    static final Set<String> SMOKING = Set.of("NEVER", "FORMER", "CURRENT", "UNKNOWN");
    static final Set<String> ALCOHOL = Set.of("NONE", "OCCASIONAL", "REGULAR", "HEAVY", "UNKNOWN");
    static final Set<String> SUBSTANCE = Set.of("NONE", "PAST", "CURRENT", "UNKNOWN");
    static final Set<String> FAMILY_STATUSES = Set.of("ACTIVE", "ENTERED_IN_ERROR");

    private HistoryRules() {}

    /** Trimmed text, blank becomes null, longer than max is refused with the field name. */
    static String text(String v, int max, String field) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        if (t.length() > max) throw new IllegalArgumentException(field + " must be at most " + max + " characters.");
        return t;
    }

    static String required(String v, int max, String field) {
        String t = text(v, max, field);
        if (t == null) throw new IllegalArgumentException(field + " is required.");
        return t;
    }

    /** Upper-cased and checked against the allowed values; blank gives the default (which may be null). */
    static String oneOf(String v, Set<String> allowed, String field, String dflt) {
        if (v == null || v.isBlank()) return dflt;
        String u = v.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!allowed.contains(u)) throw new IllegalArgumentException(field + " is not one of: " + String.join(", ", new java.util.TreeSet<>(allowed)) + ".");
        return u;
    }

    static Short ageAtOnset(Integer v) {
        if (v == null) return null;
        if (v < 0 || v > 120) throw new IllegalArgumentException("Age at onset must be between 0 and 120.");
        return v.shortValue();
    }

    /** Medical aid: scheme and member number are needed to claim; the rest is optional. */
    record Aid(String schemeName, String planName, String memberNumber, String dependentCode, String principalMember, String phone) {}

    static Aid aid(String scheme, String plan, String member, String depCode, String principal, String phone) {
        return new Aid(required(scheme, 100, "Scheme name"), text(plan, 100, "Plan"), required(member, 50, "Member number"),
                text(depCode, 10, "Dependant code"), text(principal, 200, "Principal member"), text(phone, 30, "Scheme phone"));
    }
}
