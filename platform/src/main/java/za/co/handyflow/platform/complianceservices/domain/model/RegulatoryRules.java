package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What a regulatory change must look like, who may act on it, and how urgent it is. Pure, so it can be tested without a database.
 * A change is only ever flagged by a person or by automation; it takes a reviewer to confirm the content and the effective date.
 */
public final class RegulatoryRules {

    private RegulatoryRules() {}

    public static final Set<String> KINDS = Set.of("LEGISLATION", "REGULATION", "NOTICE", "GUIDANCE", "OTHER");
    public static final Set<String> CHANGE_TYPES = Set.of("NEW", "AMENDED", "REPEALED", "PROPOSED", "GUIDANCE");
    /** Types that can be confirmed without an effective date: a proposal is not yet law and guidance has no date it starts to apply. */
    public static final Set<String> DATELESS_TYPES = Set.of("PROPOSED", "GUIDANCE");
    public static final int MIN_NOTE = 15;
    public static final int SOON_DAYS = 30;
    public static final int URGENT_DAYS = 7;
    public static final int REVIEW_STALE_DAYS = 7;

    public static String text(String raw, String what, int max, boolean required) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) {
            if (required) throw new IllegalArgumentException(what + " is required");
            return null;
        }
        if (t.length() > max) throw new IllegalArgumentException(what + " is too long (at most " + max + " characters)");
        return t;
    }

    /** Only web addresses, and only recorded. The platform never fetches them. */
    public static String url(String raw) {
        String t = text(raw, "Web address", 500, false);
        if (t == null) return null;
        String lower = t.toLowerCase();
        if (!(lower.startsWith("https://") || lower.startsWith("http://")) || t.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("The web address must start with http:// or https:// and have no spaces");
        }
        return t;
    }

    public static String kind(String raw) {
        String k = raw == null || raw.isBlank() ? "OTHER" : raw.trim().toUpperCase();
        if (!KINDS.contains(k)) throw new IllegalArgumentException("Unknown kind of source: " + raw);
        return k;
    }

    public static String changeType(String raw) {
        String t = raw == null ? "" : raw.trim().toUpperCase();
        if (!CHANGE_TYPES.contains(t)) throw new IllegalArgumentException("Say whether the change is new, amended, repealed, proposed or guidance");
        return t;
    }

    public static void checkDates(LocalDate published, LocalDate effective, LocalDate today) {
        if (published != null && published.isAfter(today)) throw new IllegalArgumentException("The publication date cannot be in the future");
        if (effective != null && effective.isAfter(today.plusYears(10))) throw new IllegalArgumentException("The effective date is more than 10 years away. Please check it");
    }

    /** The reason a change cannot be confirmed yet, or null. */
    public static String confirmProblem(String changeType, LocalDate effective) {
        if (effective == null && !DATELESS_TYPES.contains(changeType)) return "Confirm the date the change takes effect first";
        return null;
    }

    public static String reviewNote(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() < MIN_NOTE) throw new IllegalArgumentException("The review needs a note of at least " + MIN_NOTE + " characters");
        return t.length() > 2000 ? t.substring(0, 2000) : t;
    }

    /** The same person who raised the change reviews it only with a recorded reason. Returns the cleaned reason, or null when it is not needed. */
    public static String exception(UUID raisedBy, UUID reviewer, String reason) {
        if (reviewer == null) throw new IllegalArgumentException("A reviewer is required");
        if (raisedBy == null || !raisedBy.equals(reviewer)) return null;
        String r = reason == null ? "" : reason.trim();
        if (r.length() < MIN_NOTE) {
            throw new IllegalArgumentException("You raised this change, so someone else should review it. If no one else can, give a reason of at least " + MIN_NOTE + " characters");
        }
        return r.length() > 1000 ? r.substring(0, 1000) : r;
    }

    // ---- impacts ----

    public static final Set<String> IMPACT_ACTIONS = Set.of("NOTICE_GIVEN", "NO_ACTION", "DONE", "REOPEN");

    /** What can be done to an impact from its current state. */
    public static List<String> impactActions(String status) {
        return switch (status) {
            case "OPEN" -> List.of("NOTICE_GIVEN", "NO_ACTION", "DONE");
            case "NOTICE_GIVEN" -> List.of("DONE", "REOPEN");
            case "NO_ACTION", "DONE" -> List.of("REOPEN");
            default -> List.of();
        };
    }

    public static boolean impactLive(String status) { return "OPEN".equals(status) || "NOTICE_GIVEN".equals(status); }

    public static List<String> changeActions(String status) {
        return switch (status) {
            case "DETECTED" -> List.of("EDIT", "START_REVIEW", "DISMISS");
            case "IN_REVIEW" -> List.of("EDIT", "CONFIRM", "DISMISS");
            default -> List.of();
        };
    }

    public static boolean canEdit(String status) { return "DETECTED".equals(status) || "IN_REVIEW".equals(status); }

    // ---- the work queue ----

    /** A change waiting for someone to confirm it. Admins only. */
    public static WorkQueueRules.Item reviewItem(LocalDate today, String title, String status, java.time.Instant raisedAt) {
        long age = raisedAt == null ? 0 : ChronoUnit.DAYS.between(raisedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate(), today);
        String detail = ("DETECTED".equals(status) ? "Flagged" : "In review") + (age <= 0 ? " today" : " " + age + (age == 1 ? " day ago" : " days ago"));
        return new WorkQueueRules.Item("REGULATORY_REVIEW", age > REVIEW_STALE_DAYS ? WorkQueueRules.MEDIUM : WorkQueueRules.LOW, null, null, title, detail, null, "regulatory", true);
    }

    /** A client the change touches that has not yet been dealt with. Urgent once the change is in force or about to be. */
    public static WorkQueueRules.Item impactItem(LocalDate today, UUID clientId, String clientName, String title, String status, LocalDate effective) {
        String severity = WorkQueueRules.LOW;
        String when = "no effective date";
        if (effective != null) {
            long days = ChronoUnit.DAYS.between(today, effective);
            severity = days <= URGENT_DAYS ? WorkQueueRules.HIGH : days <= SOON_DAYS ? WorkQueueRules.MEDIUM : WorkQueueRules.LOW;
            when = days < 0 ? "in force since " + effective : days == 0 ? "in force today" : "in force from " + effective + " (" + days + (days == 1 ? " day)" : " days)");
        }
        String what = "NOTICE_GIVEN".equals(status) ? "client told, work not finished" : "client not yet dealt with";
        return new WorkQueueRules.Item("REGULATORY_IMPACT", severity, clientId, clientName, title, what + " · " + when, effective, "regulatory", false);
    }
}
