package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Rules for work given to outside providers, with no database in them. Work starts only when a conflict-of-interest declaration is on record
 * (approved by someone else if a conflict was declared), the provider is active, and a checked accreditation is held unless the practice
 * recorded why none is needed. Ratings describe one piece of work; they are never turned into a score for the provider.
 */
public final class ProviderWorkRules {

    private ProviderWorkRules() {}

    public static final String PROPOSED = "PROPOSED", ACTIVE = "ACTIVE", COMPLETED = "COMPLETED", CANCELLED = "CANCELLED";
    public static final String PENDING = "PENDING", NONE = "NONE", DECLARED = "DECLARED";
    public static final String EXPECTED = "EXPECTED", RECEIVED = "RECEIVED", ACCEPTED = "ACCEPTED", REJECTED = "REJECTED", WAIVED = "WAIVED";
    /** A proposed assignment still without a cleared conflict declaration this long is raised in the work queue. */
    public static final int STALLED_DAYS = 7;

    public record Performance(int completed, int onTime, Double averageRating) {}

    /** Why this assignment cannot start, or null when it can. */
    public static String activationProblem(String status, String conflictStatus, boolean conflictApproved, boolean providerActive, boolean accreditationRequired, boolean hasUsableAccreditation) {
        if (!PROPOSED.equals(status)) return "Only a proposed assignment can be started";
        if (!providerActive) return "This provider is inactive";
        if (PENDING.equals(conflictStatus)) return "Record the provider's conflict-of-interest declaration first";
        if (DECLARED.equals(conflictStatus) && !conflictApproved) return "A conflict was declared. An administrator other than the person who recorded it must approve before work starts";
        if (accreditationRequired && !hasUsableAccreditation) return "The provider has no checked accreditation that is still valid. Have one checked, or record why none is needed";
        return null;
    }

    public static void checkRating(int rating) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("The rating is from 1 to 5");
    }

    public static boolean onTime(LocalDate dueDate, LocalDate completedOn) {
        return dueDate == null || !completedOn.isAfter(dueDate);
    }

    /** Someone other than the person who received a deliverable must accept or reject it. */
    public static void checkReviewer(UUID receivedBy, UUID reviewer) {
        if (reviewer == null) throw new IllegalArgumentException("Someone must record this");
        if (reviewer.equals(receivedBy)) throw new IllegalStateException("Someone other than the person who received it must review a deliverable");
    }

    /** Every deliverable must be accepted or waived before the work is complete. */
    public static boolean deliverablesDone(Iterable<String> statuses) {
        for (String s : statuses) if (!ACCEPTED.equals(s) && !WAIVED.equals(s)) return false;
        return true;
    }

    public static LocalDate dueDate(LocalDate due, LocalDate today) {
        if (due != null && due.isBefore(today)) throw new IllegalArgumentException("The due date is in the past");
        return due;
    }

    /** Work in progress past its date is urgent; a proposal that has sat without clearance is a reminder. */
    public static WorkQueueRules.Item assignmentItem(String providerName, String title, UUID clientId, String clientName, String status, String conflictStatus, boolean conflictApproved,
                                                      LocalDate dueDate, LocalDate createdOn, LocalDate today) {
        String text = providerName + ": " + title;
        if (ACTIVE.equals(status) && dueDate != null && dueDate.isBefore(today)) {
            long late = ChronoUnit.DAYS.between(dueDate, today);
            return new WorkQueueRules.Item("PROVIDER_WORK", late > 7 ? WorkQueueRules.HIGH : WorkQueueRules.MEDIUM, clientId, clientName, text, "Provider work " + late + (late == 1 ? " day" : " days") + " late", dueDate, "providers", false);
        }
        if (PROPOSED.equals(status)) {
            boolean waiting = PENDING.equals(conflictStatus) || (DECLARED.equals(conflictStatus) && !conflictApproved);
            long days = ChronoUnit.DAYS.between(createdOn, today);
            if (waiting && days >= STALLED_DAYS) {
                return new WorkQueueRules.Item("PROVIDER_WORK", WorkQueueRules.LOW, clientId, clientName, text,
                        PENDING.equals(conflictStatus) ? "Waiting for a conflict declaration for " + days + " days" : "A declared conflict has waited for approval for " + days + " days", dueDate, "providers", false);
            }
        }
        return null;
    }

    public static WorkQueueRules.Item deliverableItem(String providerName, String assignmentTitle, String deliverable, UUID clientId, String clientName, LocalDate receivedOn, LocalDate today) {
        long days = ChronoUnit.DAYS.between(receivedOn, today);
        return new WorkQueueRules.Item("PROVIDER_DELIVERABLE", days > 3 ? WorkQueueRules.MEDIUM : WorkQueueRules.LOW, clientId, clientName, providerName + ": " + deliverable,
                "Received " + days + (days == 1 ? " day" : " days") + " ago and not yet reviewed (" + assignmentTitle + ")", null, "providers", false);
    }
}
