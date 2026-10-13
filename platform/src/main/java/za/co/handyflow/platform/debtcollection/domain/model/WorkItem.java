package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A specific action a collector must take on a case — the unit the "My Work"
 * queue is made of. The case stays the durable record; work items are the
 * operational layer around it (a missed instalment and a follow-up due are two
 * separate items even on the same case).
 * <p>
 * Priority is explainable: priorityScore + priorityReasons come from
 * WorkItemPriorityCalculator, and a supervisor may override with a mandatory
 * reason (the calculated score is kept, never overwritten).
 * <p>
 * An item only clears via complete(), which requires a disposition valid for
 * its type — ticking a box is not enough.
 * <p>
 * sourceKey makes generation idempotent: the same trigger (e.g. instalment X
 * due on date D) never raises a second item, even after the first was completed.
 */
@Entity
@Table(name = "debtcollection_work_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkItem extends AggregateRoot<WorkItem> {

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkItemType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkItemStatus status;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "priority_score", nullable = false)
    private int priorityScore;

    @Column(name = "priority_reasons", columnDefinition = "TEXT")
    private String priorityReasons;

    @Column(name = "priority_override")
    private Integer priorityOverride;

    @Column(name = "priority_override_reason", length = 500)
    private String priorityOverrideReason;

    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    @Column(name = "assigned_to_user_name", length = 255)
    private String assignedToUserName;

    @Column(name = "snoozed_until")
    private LocalDate snoozedUntil;

    @Column(name = "snooze_reason", length = 500)
    private String snoozeReason;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private WorkItemDisposition disposition;

    @Column(name = "disposition_notes", columnDefinition = "TEXT")
    private String dispositionNotes;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "source_key", length = 120)
    private String sourceKey;

    @Column(name = "created_by")
    private UUID createdBy;

    public static WorkItem raise(TenantId tenantId, UUID caseId, WorkItemType type, String title, LocalDate dueDate,
                                 UUID assignedToUserId, String assignedToUserName, String sourceKey, UUID createdBy) {
        if (caseId == null || type == null) {
            throw new IllegalArgumentException("caseId and type are required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        WorkItem w = new WorkItem();
        w.initTenantId(tenantId);
        w.caseId = caseId;
        w.type = type;
        w.status = WorkItemStatus.OPEN;
        w.title = title.length() > 500 ? title.substring(0, 500) : title;
        w.dueDate = dueDate;
        w.assignedToUserId = assignedToUserId;
        w.assignedToUserName = assignedToUserName;
        w.sourceKey = sourceKey;
        w.createdBy = createdBy;
        return w;
    }

    public boolean isActionable() {
        return status == WorkItemStatus.OPEN;
    }

    public boolean isLive() {
        return status == WorkItemStatus.OPEN || status == WorkItemStatus.SNOOZED;
    }

    public int effectivePriority() {
        return priorityOverride != null ? priorityOverride : priorityScore;
    }

    public void rescore(int score, String reasons) {
        this.priorityScore = score;
        this.priorityReasons = reasons;
    }

    /** Pass priority == null to clear an override. A reason is always required so overrides stay auditable. */
    public void overridePriority(Integer priority, String reason) {
        requireLive();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to override priority");
        }
        if (priority != null && (priority < 0 || priority > 100)) {
            throw new IllegalArgumentException("priority must be between 0 and 100");
        }
        this.priorityOverride = priority;
        this.priorityOverrideReason = reason;
    }

    public void assign(UUID userId, String userName) {
        requireLive();
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        this.assignedToUserId = userId;
        this.assignedToUserName = userName;
    }

    public void snooze(LocalDate until, String reason) {
        requireLive();
        if (until == null || !until.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Snooze date must be in the future");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to snooze a work item");
        }
        this.status = WorkItemStatus.SNOOZED;
        this.snoozedUntil = until;
        this.snoozeReason = reason;
    }

    public void wake() {
        if (status != WorkItemStatus.SNOOZED) {
            throw new IllegalStateException("Only a snoozed work item can be woken");
        }
        this.status = WorkItemStatus.OPEN;
        this.snoozedUntil = null;
    }

    public void complete(WorkItemDisposition disposition, String notes, UUID completedBy) {
        requireLive();
        if (disposition == null) {
            throw new IllegalArgumentException("A disposition is required to complete a work item");
        }
        if (!type.allowedDispositions().contains(disposition)) {
            throw new IllegalArgumentException(disposition + " cannot clear a " + type + " work item; allowed: "
                    + type.allowedDispositions());
        }
        if (disposition.requiresNotes() && (notes == null || notes.isBlank())) {
            throw new IllegalArgumentException("Notes are required when the disposition is " + disposition);
        }
        this.status = WorkItemStatus.COMPLETED;
        this.disposition = disposition;
        this.dispositionNotes = notes;
        this.completedAt = Instant.now();
        this.completedBy = completedBy;
    }

    /** System-driven (case closed/deleted) — not a collector action, so no disposition. */
    public void cancel(String reason) {
        if (!isLive()) return;
        this.status = WorkItemStatus.CANCELLED;
        this.dispositionNotes = reason;
        this.completedAt = Instant.now();
    }

    private void requireLive() {
        if (!isLive()) {
            throw new IllegalStateException("Work item is " + status + " and can no longer be changed");
        }
    }
}
