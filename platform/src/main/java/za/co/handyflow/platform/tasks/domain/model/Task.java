package za.co.handyflow.platform.tasks.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Task {

    /** The business time zone. Dates such as "due" and "overdue" follow it, not the server's zone. */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Johannesburg");

    /** Today in the business time zone, so a task turns overdue at local midnight on any server. */
    public static LocalDate today() { return LocalDate.now(BUSINESS_ZONE); }

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "board_id",  nullable = false) private UUID   boardId;
    @Column(name = "column_id", nullable = false) private UUID   columnId;
    @Column(nullable = false)                      private String title;
    private String description;
    @Column(nullable = false) private String   priority   = "NORMAL";
    @Column(nullable = false) private String   status     = "TODO";
    @Column(name = "assignee_id")       private UUID       assigneeId;
    @Column(name = "due_date")          private LocalDate  dueDate;
    @Column(name = "estimated_hours")   private BigDecimal estimatedHours;
    @Column(name = "sort_order")        private int        sortOrder = 0;

    // Cross-module link
    @Column(name = "linked_entity_type") private String linkedEntityType;
    @Column(name = "linked_entity_id")   private UUID   linkedEntityId;

    @Column(name = "created_by")   private UUID    createdBy;
    @Column(name = "created_at")   private Instant createdAt;
    @Column(name = "updated_at")   private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "deleted_at")   private Instant deletedAt;

    /**
     * FIX (notifications): set the first time TASK_OVERDUE fires for this task,
     * so the daily scheduler sweep doesn't re-notify every day a task stays
     * overdue. Cleared whenever the due date changes (see update()) so a
     * rescheduled task is eligible to alert again against its new date.
     */
    @Column(name = "overdue_alert_sent_at") private Instant overdueAlertSentAt;

    @Version private Long version;

    public static Task create(TenantId tenantId, UUID boardId, UUID columnId,
                              String title, String description, String priority,
                              UUID assigneeId, LocalDate dueDate,
                              BigDecimal estimatedHours, int sortOrder,
                              String linkedEntityType, UUID linkedEntityId,
                              UUID createdBy) {
        Task t             = new Task();
        t.tenantId         = tenantId;
        t.boardId          = boardId;
        t.columnId         = columnId;
        t.title            = title;
        t.description      = description;
        t.priority         = priority != null ? priority : "NORMAL";
        t.assigneeId       = assigneeId;
        t.dueDate          = dueDate;
        t.estimatedHours   = estimatedHours;
        t.sortOrder        = sortOrder;
        t.linkedEntityType = linkedEntityType;
        t.linkedEntityId   = linkedEntityId;
        t.createdBy        = createdBy;
        t.status           = "TODO";
        t.createdAt        = Instant.now();
        t.updatedAt        = Instant.now();
        return t;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void moveToColumn(UUID newColumnId, TaskCategory category) {
        this.columnId = newColumnId;
        applyCategory(category);
        touch();
    }

    /**
     * The single place a task's status follows its column: status becomes the column's category,
     * completedAt is set on entering a done column and cleared on leaving it.
     */
    public void applyCategory(TaskCategory category) {
        TaskCategory c = category != null ? category : TaskCategory.TODO;
        this.status = c.name();
        if (c == TaskCategory.DONE) {
            if (this.completedAt == null) this.completedAt = Instant.now();
        } else {
            this.completedAt = null;
        }
    }

    /** Sets the position within the column (0 = top). */
    public void reposition(int sortOrder) { this.sortOrder = sortOrder; }

    /**
     * Applies a task edit. A null value means "leave unchanged"; to CLEAR a field the caller sets
     * the matching clear flag (a plain null could not tell "no change" from "remove it").
     */
    public void update(String title, String description, String priority,
                       UUID assigneeId, LocalDate dueDate,
                       BigDecimal estimatedHours, String linkedEntityType,
                       UUID linkedEntityId,
                       boolean clearAssignee, boolean clearDueDate,
                       boolean clearDescription, boolean clearEstimatedHours,
                       boolean clearLink) {
        if (title != null) this.title = title;

        if (clearDescription)           this.description = null;
        else if (description != null)   this.description = description;

        if (priority != null) this.priority = priority;

        if (clearAssignee)              this.assigneeId = null;
        else if (assigneeId != null)    this.assigneeId = assigneeId;

        if (clearDueDate) {
            this.dueDate            = null;
            this.overdueAlertSentAt = null;
        } else if (dueDate != null && !dueDate.equals(this.dueDate)) {
            this.dueDate            = dueDate;
            this.overdueAlertSentAt = null; // re-arm: new deadline, eligible to alert again
        }

        if (clearEstimatedHours)        this.estimatedHours = null;
        else if (estimatedHours != null) this.estimatedHours = estimatedHours;

        if (clearLink) {
            this.linkedEntityType = null;
            this.linkedEntityId   = null;
        } else {
            if (linkedEntityType != null) this.linkedEntityType = linkedEntityType;
            if (linkedEntityId   != null) this.linkedEntityId   = linkedEntityId;
        }
        touch();
    }

    public void complete() {
        this.status      = "DONE";
        this.completedAt = Instant.now();
        touch();
    }

    public void cancel() {
        this.status = "CANCELLED";
        touch();
    }

    public void reopen(UUID columnId) {
        this.status      = "TODO";
        this.columnId    = columnId;
        this.completedAt = null;
        touch();
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public boolean isOverdue() {
        return dueDate != null
                && !"DONE".equals(status)
                && !"CANCELLED".equals(status)
                && today().isAfter(dueDate);
    }

    /** Marks the overdue alert as sent — mirrors Trip.markLongRunningAlertSent() in Fleet. */
    public void markOverdueAlertSent() {
        this.overdueAlertSentAt = Instant.now();
    }
}