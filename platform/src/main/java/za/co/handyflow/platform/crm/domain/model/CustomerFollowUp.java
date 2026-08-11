package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * CustomerFollowUp — "call this lead back in 3 days" scheduling.
 *
 * FIX: "no task/follow-up reminder system" gap — addNote() records what
 * HAPPENED; nothing scheduled what should happen NEXT. This is that
 * missing piece: a due date, a note, an assignee, and a completion state.
 * <p>
 * Deliberately its own entity rather than reusing CustomerActivity —
 * activities are immutable, append-only history (see CustomerActivity's
 * own doc comment); a follow-up is a mutable piece of future work that
 * gets completed or reassigned. Conflating the two would mean either
 * making activities mutable (breaking their audit-log guarantee) or
 * tracking "is this done yet" state on something meant to represent an
 * event that already happened.
 */
@Entity
@Table(name = "customer_followups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerFollowUp {

    /**
     * FIX: "what if follow-ups were unsuccessful, rescheduled and all" —
     * completion used to be binary (done / not done), which genuinely
     * can't represent "I called, no answer" vs "resolved" vs "need to try
     * again on a different date." RESCHEDULED doesn't just record an
     * outcome — see complete()/service.complete() — it creates a brand
     * new CustomerFollowUp linked back via rescheduledFromId, so a lead
     * that took three attempts shows as three real, distinct records with
     * their own outcomes, not one row silently edited three times.
     */
    public enum Outcome { COMPLETED, NO_RESPONSE, RESCHEDULED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, columnDefinition = "text")
    private String note;

    /** Who's on the hook to actually do this. Defaults to the creator if not explicitly assigned. */
    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 20)
    private Outcome outcome;

    /**
     * Set only on the NEW follow-up created by a RESCHEDULED completion —
     * points back to the original attempt. Null for a follow-up that
     * wasn't itself created by a reschedule (including the very first
     * attempt on any lead).
     */
    @Column(name = "rescheduled_from_id")
    private UUID rescheduledFromId;

    /** Edge-trigger guard — see CustomerFollowUpReminderScheduler for why this exists. */
    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static CustomerFollowUp create(TenantId tenantId, UUID customerId, LocalDate dueDate,
                                          String note, UUID assignedTo, UUID createdBy) {
        return create(tenantId, customerId, dueDate, note, assignedTo, createdBy, null);
    }

    /** Overload used when this follow-up exists because an earlier one was rescheduled — see Outcome.RESCHEDULED. */
    public static CustomerFollowUp create(TenantId tenantId, UUID customerId, LocalDate dueDate,
                                          String note, UUID assignedTo, UUID createdBy,
                                          UUID rescheduledFromId) {
        var f = new CustomerFollowUp();
        f.tenantId   = tenantId;
        f.customerId = customerId;
        f.dueDate    = dueDate;
        f.note       = note;
        f.assignedTo = assignedTo != null ? assignedTo : createdBy;
        f.createdBy  = createdBy;
        f.rescheduledFromId = rescheduledFromId;
        f.createdAt  = Instant.now();
        f.updatedAt  = Instant.now();
        return f;
    }

    public boolean isCompleted() { return completedAt != null; }
    public boolean isOverdue()   { return !isCompleted() && dueDate.isBefore(LocalDate.now()); }

    /** FIX: completion now requires an outcome — see the Outcome enum's own doc comment for why. */
    public void complete(Outcome outcome, UUID completedByUserId) {
        this.completedAt = Instant.now();
        this.completedBy = completedByUserId;
        this.outcome     = outcome;
        this.updatedAt   = Instant.now();
    }

    /** Undo a mistaken complete — a genuinely common action ("oops, wrong one"), not an edge case to skip. */
    public void reopen() {
        this.completedAt = null;
        this.completedBy = null;
        this.outcome     = null;
        this.updatedAt   = Instant.now();
    }

    public void markReminderSent() {
        this.reminderSentAt = Instant.now();
        this.updatedAt      = Instant.now();
    }
}