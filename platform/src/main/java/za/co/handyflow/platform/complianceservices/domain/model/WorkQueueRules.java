package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * What needs a person's attention, and in what order. Pure, so the ordering and the thresholds can be tested.
 * The queue holds things that are already recorded; it never creates work of its own.
 */
public final class WorkQueueRules {

    private WorkQueueRules() {}

    public static final int DEADLINE_SOON_DAYS = 14;
    public static final int DEADLINE_URGENT_DAYS = 3;
    public static final int DOCUMENT_EXPIRY_DAYS = 30;
    public static final int REVIEW_STALE_DAYS = 7;
    public static final int DRAFT_STALE_DAYS = 14;

    public static final String HIGH = "HIGH";
    public static final String MEDIUM = "MEDIUM";
    public static final String LOW = "LOW";

    /** {@code type} is one of DEADLINE, DOCUMENT_REVIEW, DOCUMENT_EXPIRY, APPROVAL, CATALOGUE_REVIEW, ASSESSMENT. */
    public record Item(String type, String severity, UUID clientId, String clientName, String title, String detail, LocalDate dueDate, String tab, boolean adminOnly) {}

    private static int rank(String severity) { return switch (severity) { case HIGH -> 0; case MEDIUM -> 1; default -> 2; }; }

    /** Overdue is always high. Within three days is high, within two weeks is medium. Further out is not queue material. */
    public static Item deadline(LocalDate today, UUID clientId, String clientName, String what, LocalDate due) {
        long days = ChronoUnit.DAYS.between(today, due);
        if (days > DEADLINE_SOON_DAYS) return null;
        String sev = days < 0 || days <= DEADLINE_URGENT_DAYS ? HIGH : MEDIUM;
        String detail = days < 0 ? (days == -1 ? "1 day overdue" : -days + " days overdue") : days == 0 ? "Due today" : days == 1 ? "Due tomorrow" : "Due in " + days + " days";
        return new Item("DEADLINE", sev, clientId, clientName, what, detail, due, "deadlines", false);
    }

    /** A document waiting for someone to review it. It gets more urgent the longer it waits. */
    public static Item documentReview(LocalDate today, UUID clientId, String clientName, String documentType, Instant uploadedAt) {
        long age = ChronoUnit.DAYS.between(uploadedAt.atZone(ZoneOffset.UTC).toLocalDate(), today);
        String detail = age <= 0 ? "Uploaded today, waiting for review" : "Waiting for review for " + age + (age == 1 ? " day" : " days");
        return new Item("DOCUMENT_REVIEW", age > REVIEW_STALE_DAYS ? MEDIUM : LOW, clientId, clientName, documentType, detail, null, "documents", false);
    }

    /** A verified document that has expired, or will within a month. Expired is high. */
    public static Item documentExpiry(LocalDate today, UUID clientId, String clientName, String documentType, LocalDate expiry) {
        long days = ChronoUnit.DAYS.between(today, expiry);
        if (days > DOCUMENT_EXPIRY_DAYS) return null;
        String detail = days < 0 ? "Expired " + expiry : days == 0 ? "Expires today" : "Expires " + expiry + " (" + days + (days == 1 ? " day)" : " days)");
        return new Item("DOCUMENT_EXPIRY", days < 0 ? HIGH : MEDIUM, clientId, clientName, documentType, detail, expiry, "documents", false);
    }

    /** Taking an obligation off a client waits for a second person. Admins only. */
    public static Item approval(UUID clientId, String clientName, String obligationCode) {
        return new Item("APPROVAL", MEDIUM, clientId, clientName, obligationCode, "Removal is waiting for approval and still counts as applicable", null, "obligations", true);
    }

    /** A catalogue version waiting for an admin to approve it. Not tied to any client. */
    public static Item catalogueReview(LocalDate today, String code, int versionNumber, Instant submittedAt) {
        long age = submittedAt == null ? 0 : ChronoUnit.DAYS.between(submittedAt.atZone(ZoneOffset.UTC).toLocalDate(), today);
        return new Item("CATALOGUE_REVIEW", age > REVIEW_STALE_DAYS ? MEDIUM : LOW, null, null, code + " v" + versionNumber,
                age <= 0 ? "Submitted today for review" : "Waiting for review for " + age + (age == 1 ? " day" : " days"), null, "catalogue", true);
    }

    /** An assessment started and not completed. It goes stale after two weeks. */
    public static Item assessmentOpen(LocalDate today, UUID clientId, String clientName, Instant startedAt) {
        long age = ChronoUnit.DAYS.between(startedAt.atZone(ZoneOffset.UTC).toLocalDate(), today);
        return new Item("ASSESSMENT", age > DRAFT_STALE_DAYS ? MEDIUM : LOW, clientId, clientName, "Assessment in progress",
                age <= 0 ? "Started today" : "Started " + age + (age == 1 ? " day ago" : " days ago"), null, "assessments", false);
    }

    /** Most urgent first, then the earliest date, then client and title so the order never jumps about. */
    public static List<Item> order(List<Item> items) {
        return items.stream().filter(java.util.Objects::nonNull).sorted(
                Comparator.comparingInt((Item i) -> rank(i.severity()))
                        .thenComparing(Item::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Item::clientName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Item::title, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList();
    }

    public static List<Item> visibleTo(List<Item> items, boolean admin) {
        return items.stream().filter(i -> admin || !i.adminOnly()).toList();
    }
}
