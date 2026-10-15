package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What management will do about a finding. Management completes it with a note (evidence is attached through the shared evidence
 * module against entity type AUDIT_ACTION_PLAN); an auditor then validates or returns it. The person who validates may be neither
 * the person who completed it nor the plan's owner.
 */
@Entity(name = "AuditActionPlan")
@Table(name = "audit_action_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditActionPlan {
    public static final int MIN_NOTE = 10;

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "finding_id", nullable = false) private UUID findingId;
    @Column(name = "description", nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "owner") private UUID owner;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "status", nullable = false) private String status = "OPEN"; // OPEN | COMPLETED | VALIDATED | CANCELLED
    @Column(name = "completion_note", columnDefinition = "TEXT") private String completionNote;
    @Column(name = "completed_by") private UUID completedBy;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "validation_note", columnDefinition = "TEXT") private String validationNote;
    @Column(name = "validated_by") private UUID validatedBy;
    @Column(name = "validated_at") private Instant validatedAt;
    @Column(name = "times_returned", nullable = false) private int timesReturned = 0;
    @Column(name = "cancel_reason", columnDefinition = "TEXT") private String cancelReason;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    public static AuditActionPlan create(UUID tenantId, UUID findingId, String description, UUID owner, LocalDate dueDate, UUID createdBy) {
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Describe the action");
        if (dueDate == null) throw new IllegalArgumentException("Give the action a due date");
        AuditActionPlan p = new AuditActionPlan();
        p.tenantId = tenantId; p.findingId = findingId; p.description = description.trim(); p.owner = owner; p.dueDate = dueDate; p.createdBy = createdBy;
        return p;
    }

    private static String note(String n, String what) {
        if (n == null || n.trim().length() < MIN_NOTE) throw new IllegalArgumentException(what + " needs a note of at least " + MIN_NOTE + " characters");
        return n.trim();
    }

    public void complete(UUID by, String note) {
        if (!"OPEN".equals(status)) throw new IllegalStateException("Only an open action can be marked complete. Current status: " + status);
        this.completionNote = note(note, "Completing an action");
        this.completedBy = by; this.completedAt = Instant.now(); this.status = "COMPLETED";
    }

    private void requireIndependentValidator(UUID by) {
        if (!"COMPLETED".equals(status)) throw new IllegalStateException("Only a completed action can be validated or returned. Current status: " + status);
        if (by == null) throw new IllegalArgumentException("Who is validating?");
        if (by.equals(completedBy)) throw new IllegalStateException("The person who completed an action cannot validate it");
        if (by.equals(owner)) throw new IllegalStateException("The owner of an action cannot validate it");
    }

    public void validate(UUID by, String note) {
        requireIndependentValidator(by);
        this.validationNote = note(note, "Validating an action");
        this.validatedBy = by; this.validatedAt = Instant.now(); this.status = "VALIDATED";
    }

    /** Send it back: the work did not hold up. The note says why; the action reopens. */
    public void returnForRework(UUID by, String note) {
        requireIndependentValidator(by);
        this.validationNote = note(note, "Returning an action");
        this.validatedBy = by; this.validatedAt = Instant.now();
        this.timesReturned++; this.status = "OPEN";
        this.completionNote = null; this.completedBy = null; this.completedAt = null;
    }

    public void cancel(String reason) {
        if ("VALIDATED".equals(status) || "CANCELLED".equals(status)) throw new IllegalStateException("This action is already " + status.toLowerCase());
        this.cancelReason = note(reason, "Cancelling an action");
        this.status = "CANCELLED";
    }

    /** Still to be done or checked, and past its due date. */
    public boolean isOverdue(LocalDate today) {
        return ("OPEN".equals(status) || "COMPLETED".equals(status)) && dueDate.isBefore(today);
    }

    /** Stops the finding from being closed. */
    public boolean blocksClosing() {
        return "OPEN".equals(status) || "COMPLETED".equals(status);
    }
}
