package za.co.handyflow.platform.clinic.application.internal;

import java.util.List;
import java.util.Set;

/** Checks and cleans a letter template before it is saved. Pure. */
final class LetterTemplateRules {
    private LetterTemplateRules() {}

    static final Set<String> KINDS = Set.of("SICK_NOTE", "REFERRAL", "PRESCRIPTION_LETTER", "GENERAL_LETTER");
    static final Set<String> URGENCIES = Set.of("ROUTINE", "SEMI_URGENT", "URGENT");
    static final int NAME_MAX = 100, TITLE_MAX = 200, BODY_MAX = 4000, SPECIALTY_MAX = 100;

    record Clean(String kind, String name, String title, String body, String specialty, String urgency, Integer unfitDays) {}

    static Clean clean(String kind, String name, String title, String body, String specialty, String urgency, Integer unfitDays) {
        String k = kind == null ? "" : kind.trim().toUpperCase().replace(' ', '_');
        if (!KINDS.contains(k)) throw new IllegalArgumentException("Choose what kind of letter this is");
        String n = text(name, NAME_MAX, "Name");
        if (n == null) throw new IllegalArgumentException("Give the template a name");
        String b = text(body, BODY_MAX, "Text");
        String ttl = text(title, TITLE_MAX, "Title");
        // A referral may hold only the reason (its title); every other kind needs its text.
        if (b == null && !("REFERRAL".equals(k) && ttl != null)) throw new IllegalArgumentException("The template has no text");
        if ("GENERAL_LETTER".equals(k) || "PRESCRIPTION_LETTER".equals(k)) {
            if (ttl == null) throw new IllegalArgumentException("Give the letter a title");
        }
        List<String> bad = LetterMerge.unknown((b == null ? "" : b) + " " + (ttl == null ? "" : ttl));
        if (!bad.isEmpty()) throw new IllegalArgumentException("Unknown merge field " + String.join(", ", bad.stream().map(x -> "{{" + x + "}}").toList())
                + ". Use: " + String.join(", ", LetterMerge.FIELDS.stream().map(x -> "{{" + x + "}}").toList()));
        String u = urgency == null || urgency.isBlank() ? null : urgency.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if (u != null && !URGENCIES.contains(u)) throw new IllegalArgumentException("Urgency must be routine, semi-urgent or urgent");
        if (u != null && !"REFERRAL".equals(k)) u = null;
        Integer days = unfitDays;
        if (days != null && (days < 1 || days > 365)) throw new IllegalArgumentException("Days unfit must be between 1 and 365");
        if (days != null && !"SICK_NOTE".equals(k)) days = null;
        String spec = "REFERRAL".equals(k) ? text(specialty, SPECIALTY_MAX, "Specialty") : null;
        return new Clean(k, n, "SICK_NOTE".equals(k) ? null : ttl, b, spec, u, days);
    }

    static String text(String v, int max, String field) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim();
        if (s.length() > max) throw new IllegalArgumentException(field + " is longer than " + max + " characters");
        return s;
    }
}
