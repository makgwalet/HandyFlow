package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A supplier quote that becomes a commitment once approved.
 * <p>
 * QUOTED     — a price someone has asked for; counts for nothing yet.
 * APPROVED   — the organisation is committed to spend this (counts in "committed").
 * COMPLETED  — delivered and closed; its final cost is what was actually invoiced.
 * CANCELLED  — dropped; counts for nothing.
 * <p>
 * Once approved, amount / budget line / supplier are locked — changing what was
 * agreed means cancelling and raising a new commitment, which leaves a trail.
 */
@Entity
@Table(name = "event_commitments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventCommitment {

    public static final String QUOTED = "QUOTED";
    public static final String APPROVED = "APPROVED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)       private UUID tenantId;
    @Column(name = "event_id", nullable = false)        private UUID eventId;
    @Column(name = "budget_line_id", nullable = false)  private UUID budgetLineId;
    @Column(name = "vendor_id")                         private UUID vendorId;
    @Column(name = "description", nullable = false)     private String description;
    @Column(name = "amount", nullable = false)          private BigDecimal amount;
    @Column(name = "status", nullable = false)          private String status = QUOTED;

    @Column(name = "quote_reference")     private String quoteReference;
    @Column(name = "po_reference")        private String poReference;
    @Column(name = "contract_reference")  private String contractReference;
    @Column(name = "deliverable")         private String deliverable;
    @Column(name = "due_date")            private LocalDate dueDate;
    @Column(name = "deposit_amount")      private BigDecimal depositAmount;
    @Column(name = "deposit_due_date")    private LocalDate depositDueDate;

    @Column(name = "approved_by")         private UUID approvedBy;
    @Column(name = "approved_by_name")    private String approvedByName;
    @Column(name = "approved_at")         private Instant approvedAt;
    @Column(name = "over_budget_reason")  private String overBudgetReason;
    @Column(name = "cancelled_reason")    private String cancelledReason;

    @Column(name = "created_by")                    private UUID createdBy;
    @Column(name = "created_at", nullable = false)  private Instant createdAt;
    @Column(name = "updated_at", nullable = false)  private Instant updatedAt;

    public static EventCommitment create(TenantId tenantId, UUID eventId, UUID budgetLineId, UUID vendorId,
                                         String description, BigDecimal amount, String quoteReference,
                                         String poReference, String contractReference, String deliverable,
                                         LocalDate dueDate, BigDecimal depositAmount, LocalDate depositDueDate,
                                         UUID createdBy) {
        EventCommitment c = new EventCommitment();
        c.id        = UUID.randomUUID();
        c.tenantId  = tenantId.getValue();
        c.eventId   = eventId;
        c.createdBy = createdBy;
        c.status    = QUOTED;
        c.createdAt = Instant.now();
        c.revise(budgetLineId, vendorId, description, amount, quoteReference, poReference,
                contractReference, deliverable, dueDate, depositAmount, depositDueDate);
        return c;
    }

    /** Full edit — only while still a quote. */
    public void revise(UUID budgetLineId, UUID vendorId, String description, BigDecimal amount,
                       String quoteReference, String poReference, String contractReference,
                       String deliverable, LocalDate dueDate, BigDecimal depositAmount,
                       LocalDate depositDueDate) {
        if (!QUOTED.equals(status))
            throw new IllegalStateException("Only a QUOTED commitment can be fully edited (status is " + status + ")");
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Amount must be greater than zero");
        this.budgetLineId = budgetLineId;
        this.vendorId     = vendorId;
        this.description  = description.trim();
        this.amount       = amount.setScale(2, RoundingMode.HALF_UP);
        applyAdministrative(quoteReference, poReference, contractReference, deliverable, dueDate,
                depositAmount, depositDueDate);
    }

    /** What may still change after approval: references, deliverable, dates and the deposit schedule. */
    public void updateAdministrative(String quoteReference, String poReference, String contractReference,
                                     String deliverable, LocalDate dueDate, BigDecimal depositAmount,
                                     LocalDate depositDueDate) {
        if (CANCELLED.equals(status))
            throw new IllegalStateException("A cancelled commitment cannot be edited");
        applyAdministrative(quoteReference, poReference, contractReference, deliverable, dueDate,
                depositAmount, depositDueDate);
    }

    private void applyAdministrative(String quoteReference, String poReference, String contractReference,
                                     String deliverable, LocalDate dueDate, BigDecimal depositAmount,
                                     LocalDate depositDueDate) {
        if (depositAmount != null && depositAmount.signum() < 0)
            throw new IllegalArgumentException("Deposit cannot be negative");
        if (depositAmount != null && depositAmount.compareTo(amount) > 0)
            throw new IllegalArgumentException("Deposit cannot be more than the commitment amount");
        if (depositDueDate != null && (depositAmount == null || depositAmount.signum() == 0))
            throw new IllegalArgumentException("A deposit due date needs a deposit amount");
        this.quoteReference    = quoteReference;
        this.poReference       = poReference;
        this.contractReference = contractReference;
        this.deliverable       = deliverable;
        this.dueDate           = dueDate;
        this.depositAmount     = depositAmount != null ? depositAmount.setScale(2, RoundingMode.HALF_UP) : null;
        this.depositDueDate    = depositDueDate;
        this.updatedAt         = Instant.now();
    }

    public void approve(UUID by, String byName, String overBudgetReason) {
        if (!QUOTED.equals(status))
            throw new IllegalStateException("Only a QUOTED commitment can be approved (status is " + status + ")");
        this.status           = APPROVED;
        this.approvedBy       = by;
        this.approvedByName   = byName;
        this.approvedAt       = Instant.now();
        this.overBudgetReason = overBudgetReason;
        this.updatedAt        = Instant.now();
    }

    public void complete() {
        if (!APPROVED.equals(status))
            throw new IllegalStateException("Only an APPROVED commitment can be completed (status is " + status + ")");
        this.status    = COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        if (!QUOTED.equals(status) && !APPROVED.equals(status))
            throw new IllegalStateException("Only a QUOTED or APPROVED commitment can be cancelled (status is " + status + ")");
        this.status          = CANCELLED;
        this.cancelledReason = reason;
        this.updatedAt       = Instant.now();
    }

    /** Counts toward "committed": approved and completed commitments. */
    public boolean isCounted() {
        return APPROVED.equals(status) || COMPLETED.equals(status);
    }

    public boolean isCompleted() {
        return COMPLETED.equals(status);
    }
}