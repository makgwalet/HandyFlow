package za.co.handyflow.platform.tasks.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Task {

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

    public void moveToColumn(UUID newColumnId, boolean isDoneColumn) {
        this.columnId  = newColumnId;
        this.status    = isDoneColumn ? "DONE" : this.status;
        if (isDoneColumn && this.completedAt == null) {
            this.completedAt = Instant.now();
        } else if (!isDoneColumn) {
            this.completedAt = null;
        }
        touch();
    }

    public void update(String title, String description, String priority,
                        UUID assigneeId, LocalDate dueDate,
                        BigDecimal estimatedHours, String linkedEntityType,
                        UUID linkedEntityId) {
        if (title            != null) this.title            = title;
        if (description      != null) this.description      = description;
        if (priority         != null) this.priority         = priority;
        if (assigneeId       != null) this.assigneeId       = assigneeId;
        if (dueDate          != null) this.dueDate          = dueDate;
        if (estimatedHours   != null) this.estimatedHours   = estimatedHours;
        if (linkedEntityType != null) this.linkedEntityType = linkedEntityType;
        if (linkedEntityId   != null) this.linkedEntityId   = linkedEntityId;
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
                && LocalDate.now().isAfter(dueDate);
    }
}
