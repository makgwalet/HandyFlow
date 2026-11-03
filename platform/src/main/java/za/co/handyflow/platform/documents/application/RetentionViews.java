package za.co.handyflow.platform.documents.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What the records retention screens read and send. */
public final class RetentionViews {
    private RetentionViews() {}

    /**
     * One kept (or disposed) record. {@code policySource}: POLICY (its category's policy), MODULE_POLICY (the module's '*' policy)
     * or DEFAULT (7 years, no policy set). {@code due}: past its date, active and not held.
     */
    public record Item(UUID id, String moduleCode, String sourceType, UUID sourceId, String category, String jurisdiction,
                       String reference, String title, String contextLabel, LocalDate clockStart, LocalDate retainUntil, Integer policyYears,
                       String policySource, boolean policyConfirmed, boolean personalInfo, boolean due,
                       boolean legalHold, String holdReason, String holdByName, Instant holdAt,
                       String status, UUID batchId, Instant disposedAt, String disposalDetail) {}

    /** A policy row, or (id null, isDefault true) a category in use that has no policy and so keeps 7 years. */
    public record Policy(UUID id, String moduleCode, String category, String jurisdiction, int retainYears, boolean personalInfo,
                         boolean confirmed, String notes, String updatedByName, Instant updatedAt, long recordsKept, boolean isDefault) {}

    public record BatchItem(UUID itemId, String outcome, String detail, Instant doneAt, Item item) {}

    /** {@code items} is filled only when one request is read. */
    public record Batch(UUID id, String status, String reason, UUID proposedBy, String proposedByName, Instant proposedAt,
                        UUID decidedBy, String decidedByName, Instant decidedAt, String decisionNote, Instant cancelledAt, Instant completedAt,
                        int itemCount, int disposedCount, int skippedCount, int failedCount, List<BatchItem> items) {}

    public record Summary(long tracked, long clockNotStarted, long due, long upcoming, long held, long proposed, long disposed,
                          long openRequests, long categoriesWithoutConfirmedPolicy, int upcomingDays) {}

    public record HoldRequest(Boolean hold, String reason) {}

    public record PolicyRequest(String moduleCode, String category, String jurisdiction, Integer retainYears, Boolean personalInfo,
                                Boolean confirmed, String notes) {}

    public record ProposeRequest(List<UUID> itemIds, String reason) {}

    public record DecisionRequest(String note) {}
}
