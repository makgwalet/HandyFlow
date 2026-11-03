package za.co.handyflow.platform.documents.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A request to dispose of a set of records: proposed by one person, decided by a different person (the database refuses the same
 * person, V580). PROPOSED -> APPROVED -> COMPLETED, or PROPOSED -> REJECTED / CANCELLED. Never deleted.
 */
@Entity
@Table(name = "document_disposal_batches")
public class DisposalBatch {

    public static final String PROPOSED = "PROPOSED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";
    public static final String COMPLETED = "COMPLETED";

    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)   private UUID tenantId;
    @Column(name = "status", nullable = false)                         private String status;
    @Column(name = "reason", nullable = false, updatable = false)      private String reason;
    @Column(name = "proposed_by", nullable = false, updatable = false) private UUID proposedBy;
    @Column(name = "proposed_by_name", updatable = false)              private String proposedByName;
    @Column(name = "proposed_at", nullable = false, updatable = false) private Instant proposedAt;
    @Column(name = "decided_by")                                       private UUID decidedBy;
    @Column(name = "decided_by_name")                                  private String decidedByName;
    @Column(name = "decided_at")                                       private Instant decidedAt;
    @Column(name = "decision_note")                                    private String decisionNote;
    @Column(name = "cancelled_at")                                     private Instant cancelledAt;
    @Column(name = "completed_at")                                     private Instant completedAt;
    @Column(name = "item_count", nullable = false)                     private int itemCount;
    @Column(name = "disposed_count", nullable = false)                 private int disposedCount;
    @Column(name = "skipped_count", nullable = false)                  private int skippedCount;
    @Column(name = "failed_count", nullable = false)                   private int failedCount;

    protected DisposalBatch() {}

    private static Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }

    public static DisposalBatch propose(UUID tenantId, String reason, int itemCount, UUID by, String byName) {
        if (by == null) throw new IllegalArgumentException("A disposal must be proposed by a signed-in person.");
        DisposalBatch b = new DisposalBatch();
        b.id = UUID.randomUUID();
        b.tenantId = tenantId;
        b.status = PROPOSED;
        b.reason = RetentionRules.reason(reason, "propose a disposal");
        b.proposedBy = by;
        b.proposedByName = RetentionRules.optional(byName, 200);
        b.proposedAt = now();
        b.itemCount = itemCount;
        return b;
    }

    private void decide(String to, UUID by, String byName, String note) {
        if (!PROPOSED.equals(status)) throw new IllegalStateException("This disposal request has already been decided.");
        if (RetentionRules.sameOrUnknownPerson(proposedBy, by))
            throw new IllegalStateException("A disposal must be decided by someone other than the person who proposed it.");
        status = to;
        decidedBy = by;
        decidedByName = RetentionRules.optional(byName, 200);
        decidedAt = now();
        decisionNote = note;
    }

    public void approve(UUID by, String byName, String note) { decide(APPROVED, by, byName, RetentionRules.optional(note, 500)); }

    public void reject(UUID by, String byName, String note) { decide(REJECTED, by, byName, RetentionRules.reason(note, "reject a disposal")); }

    public void cancel(UUID by) {
        if (!PROPOSED.equals(status)) throw new IllegalStateException("Only a disposal request that is still waiting can be withdrawn.");
        if (by == null || !by.equals(proposedBy)) throw new IllegalStateException("Only the person who proposed it can withdraw a disposal request.");
        status = CANCELLED;
        cancelledAt = now();
    }

    public void complete(int disposed, int skipped, int failed) {
        if (!APPROVED.equals(status)) throw new IllegalStateException("Only an approved disposal can be completed.");
        status = COMPLETED;
        disposedCount = disposed;
        skippedCount = skipped;
        failedCount = failed;
        completedAt = now();
    }

    public boolean isOpen() { return PROPOSED.equals(status) || APPROVED.equals(status); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public UUID getProposedBy() { return proposedBy; }
    public String getProposedByName() { return proposedByName; }
    public Instant getProposedAt() { return proposedAt; }
    public UUID getDecidedBy() { return decidedBy; }
    public String getDecidedByName() { return decidedByName; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getItemCount() { return itemCount; }
    public int getDisposedCount() { return disposedCount; }
    public int getSkippedCount() { return skippedCount; }
    public int getFailedCount() { return failedCount; }
}
