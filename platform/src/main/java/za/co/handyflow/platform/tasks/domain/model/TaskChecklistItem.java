package za.co.handyflow.platform.tasks.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// ── TaskChecklistItem ────────────────────────────────────────────────────────
// Deliberately a flat checklist item, not a nested Task — the audit's
// "subtasks/checklists" gap maps to Trello/Asana-style checkable line items
// under a task, not a full second tier of Kanban-tracked sub-tasks (which
// would need its own board/column/assignee semantics, a much bigger scope
// change than what was asked for).
@Entity
@Table(name = "task_checklist_items")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TaskChecklistItem {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "task_id",   nullable = false) private UUID    taskId;
    @Column(name = "tenant_id", nullable = false) private UUID    tenantId;
    @Column(nullable = false)                      private String  text;
    @Column(nullable = false)                      private boolean completed = false;
    @Column(name = "sort_order")                   private int     sortOrder = 0;
    @Column(name = "created_at")                   private Instant createdAt;
    @Column(name = "completed_at")                 private Instant completedAt;

    public static TaskChecklistItem create(UUID taskId, UUID tenantId, String text, int sortOrder) {
        TaskChecklistItem i = new TaskChecklistItem();
        i.taskId    = taskId;
        i.tenantId  = tenantId;
        i.text      = text;
        i.sortOrder = sortOrder;
        i.createdAt = Instant.now();
        return i;
    }

    public void setCompleted(boolean completed) {
        this.completed   = completed;
        this.completedAt = completed ? Instant.now() : null;
    }

    public void updateText(String text) {
        if (text != null && !text.isBlank()) this.text = text;
    }
}