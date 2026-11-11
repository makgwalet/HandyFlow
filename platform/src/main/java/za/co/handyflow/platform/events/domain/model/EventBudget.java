package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The approval state of an event's budget. One per event. The planned
 * amounts themselves live in EventBudgetLine; this row only says whether
 * those amounts are still being drafted, are awaiting approval, or are
 * approved and therefore locked.
 * <p>
 * DRAFT -> SUBMITTED -> APPROVED, with reject (SUBMITTED -> DRAFT) and
 * reopen (SUBMITTED/APPROVED -> DRAFT). Planned amounts can only change
 * while DRAFT, so an approved figure can never be quietly edited — changing
 * it means reopening, which is audited, and approving again.
 */
@Entity
@Table(name = "event_budgets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventBudget {

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)  private UUID tenantId;
    @Column(name = "event_id", nullable = false)   private UUID eventId;
    @Column(name = "status", nullable = false)     private String status = DRAFT;

    @Column(name = "submitted_by")       private UUID submittedBy;
    @Column(name = "submitted_by_name")  private String submittedByName;
    @Column(name = "submitted_at")       private Instant submittedAt;
    @Column(name = "approved_by")        private UUID approvedBy;
    @Column(name = "approved_by_name")   private String approvedByName;
    @Column(name = "approved_at")        private Instant approvedAt;
    @Column(name = "approved_total")     private BigDecimal approvedTotal;
    @Column(name = "rejection_reason")   private String rejectionReason;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static EventBudget create(TenantId tenantId, UUID eventId) {
        EventBudget b = new EventBudget();
        b.id        = UUID.randomUUID();
        b.tenantId  = tenantId.getValue();
        b.eventId   = eventId;
        b.status    = DRAFT;
        b.createdAt = Instant.now();
        b.updatedAt = Instant.now();
        return b;
    }

    public boolean isEditable() {
        return DRAFT.equals(status);
    }

    public boolean isApproved() {
        return APPROVED.equals(status);
    }

    public void submit(UUID by, String byName) {
        if (!DRAFT.equals(status))
            throw new IllegalStateException("Only a DRAFT budget can be submitted (status is " + status + ")");
        this.status          = SUBMITTED;
        this.submittedBy     = by;
        this.submittedByName = byName;
        this.submittedAt     = Instant.now();
        this.rejectionReason = null;
        this.updatedAt       = Instant.now();
    }

    public void approve(UUID by, String byName, BigDecimal plannedTotal) {
        if (!SUBMITTED.equals(status))
            throw new IllegalStateException("Only a SUBMITTED budget can be approved (status is " + status + ")");
        this.status         = APPROVED;
        this.approvedBy     = by;
        this.approvedByName = byName;
        this.approvedAt     = Instant.now();
        this.approvedTotal  = plannedTotal;
        this.updatedAt      = Instant.now();
    }

    /** Approver sends it back for changes: SUBMITTED -> DRAFT, with the reason kept for the author. */
    public void reject(String reason) {
        if (!SUBMITTED.equals(status))
            throw new IllegalStateException("Only a SUBMITTED budget can be rejected (status is " + status + ")");
        this.status          = DRAFT;
        this.rejectionReason = reason;
        this.submittedBy     = null;
        this.submittedByName = null;
        this.submittedAt     = null;
        this.updatedAt       = Instant.now();
    }

    /** Unlocks the planned amounts. Any previous approval is cleared — the budget must be approved again. */
    public void reopen() {
        if (DRAFT.equals(status))
            throw new IllegalStateException("The budget is already open for editing");
        this.status         = DRAFT;
        this.submittedBy    = null;
        this.submittedByName = null;
        this.submittedAt    = null;
        this.approvedBy     = null;
        this.approvedByName = null;
        this.approvedAt     = null;
        this.approvedTotal  = null;
        this.updatedAt      = Instant.now();
    }
}