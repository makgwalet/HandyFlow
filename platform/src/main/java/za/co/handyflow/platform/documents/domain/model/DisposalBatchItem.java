package za.co.handyflow.platform.documents.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** One record in a disposal request and what happened to it: PENDING, then DISPOSED, SKIPPED (held, no longer due, rejected) or FAILED. */
@Entity
@Table(name = "document_disposal_batch_items")
@IdClass(DisposalBatchItem.Key.class)
public class DisposalBatchItem {

    public static final String PENDING = "PENDING";
    public static final String DISPOSED = "DISPOSED";
    public static final String SKIPPED = "SKIPPED";
    public static final String FAILED = "FAILED";

    public static class Key implements Serializable {
        private UUID batchId;
        private UUID itemId;
        public Key() {}
        public Key(UUID batchId, UUID itemId) { this.batchId = batchId; this.itemId = itemId; }
        @Override public boolean equals(Object o) { return o instanceof Key k && Objects.equals(batchId, k.batchId) && Objects.equals(itemId, k.itemId); }
        @Override public int hashCode() { return Objects.hash(batchId, itemId); }
    }

    @Id @Column(name = "batch_id") private UUID batchId;
    @Id @Column(name = "item_id")  private UUID itemId;
    @Column(name = "outcome", nullable = false) private String outcome;
    @Column(name = "detail")                    private String detail;
    @Column(name = "done_at")                   private Instant doneAt;

    protected DisposalBatchItem() {}

    public static DisposalBatchItem of(UUID batchId, UUID itemId) {
        DisposalBatchItem i = new DisposalBatchItem();
        i.batchId = batchId;
        i.itemId = itemId;
        i.outcome = PENDING;
        return i;
    }

    public void done(String outcome, String detail) {
        if (!PENDING.equals(this.outcome)) throw new IllegalStateException("This record has already been dealt with in this request.");
        this.outcome = outcome;
        this.detail = RetentionRules.optional(detail, 500);
        this.doneAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isPending() { return PENDING.equals(outcome); }
    public UUID getBatchId() { return batchId; }
    public UUID getItemId() { return itemId; }
    public String getOutcome() { return outcome; }
    public String getDetail() { return detail; }
    public Instant getDoneAt() { return doneAt; }
}
