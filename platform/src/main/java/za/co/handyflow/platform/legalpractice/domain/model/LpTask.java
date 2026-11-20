package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.legalpractice.application.internal.TaskRules;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A piece of work on a matter. It has one assignee, a due date and a priority. It is finished (DONE, can be reopened) or cancelled with a reason (final). Never deleted. */
@Entity
@Table(name = "lp_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpTask {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "matter_id", nullable = false, updatable = false) private UUID matterId;
    @Column(name = "client_id", nullable = false, updatable = false) private UUID clientId;
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 2000) private String description;
    @Column(nullable = false, length = 10) private String priority = "NORMAL";
    @Column(nullable = false, length = 12) private String status = "OPEN";
    @Column(name = "assignee_id") private UUID assigneeId;
    @Column(name = "assignee_name") private String assigneeName;
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "completed_by") private UUID completedBy;
    @Column(name = "completed_by_name") private String completedByName;
    @Column(name = "cancel_reason", length = 1000) private String cancelReason;
    @Column(name = "workflow_run_id", updatable = false) private UUID workflowRunId;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "created_by_name", updatable = false) private String createdByName;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @Version
    private Long version;

    public static LpTask create(TenantId tenantId, UUID matterId, UUID clientId, String title, String description, String priority, LocalDate dueDate,
                                UUID assigneeId, String assigneeName, UUID workflowRunId, UUID by, String byName, Instant now) {
        String problem = TaskRules.taskProblem(title, description, priority);
        if (problem != null) throw new IllegalArgumentException(problem);
        if (by == null) throw new IllegalArgumentException("A task must say who created it");
        LpTask t = new LpTask();
        t.tenantId = tenantId; t.matterId = matterId; t.clientId = clientId; t.workflowRunId = workflowRunId;
        t.createdBy = by; t.createdByName = byName; t.createdAt = now; t.updatedAt = now;
        t.apply(title, description, priority, dueDate);
        t.setAssignee(assigneeId, assigneeName);
        return t;
    }

    public boolean live() { return TaskRules.live(status); }

    private void requireLive() { if (!live()) throw new IllegalStateException("This task is " + status.toLowerCase().replace('_', ' ') + " and cannot be changed"); }

    private void apply(String title, String description, String priority, LocalDate dueDate) {
        this.title = title.trim(); this.description = description == null || description.isBlank() ? null : description.trim();
        this.priority = priority; this.dueDate = dueDate;
    }

    private void setAssignee(UUID id, String name) {
        if (id == null) { this.assigneeId = null; this.assigneeName = null; }
        else { this.assigneeId = id; this.assigneeName = name == null || name.isBlank() ? "Unknown" : name.trim(); }
    }

    public void edit(String title, String description, String priority, LocalDate dueDate, Instant now) {
        requireLive();
        String problem = TaskRules.taskProblem(title, description, priority);
        if (problem != null) throw new IllegalArgumentException(problem);
        apply(title, description, priority, dueDate);
        updatedAt = now;
    }

    /** Returns the name of the previous assignee (or null). */
    public String reassign(UUID id, String name, Instant now) {
        requireLive();
        if (java.util.Objects.equals(id, assigneeId)) throw new IllegalStateException(id == null ? "The task is already unassigned" : "The task is already assigned to that person");
        String was = assigneeName;
        setAssignee(id, name);
        updatedAt = now;
        return was;
    }

    private void move(String to, Instant now) {
        String problem = TaskRules.moveProblem(status, to);
        if (problem != null) throw new IllegalStateException(problem);
        status = to; updatedAt = now;
    }

    public void start(Instant now) { move("IN_PROGRESS", now); }

    public void stop(Instant now) { if (!"IN_PROGRESS".equals(status)) throw new IllegalStateException("This task has not been started"); move("OPEN", now); }

    public void complete(UUID by, String byName, Instant now) {
        if (by == null) throw new IllegalArgumentException("A completion must say who did it");
        move("DONE", now);
        completedAt = now; completedBy = by; completedByName = byName;
    }

    public void reopen(String reason, Instant now) {
        String problem = TaskRules.reasonProblem(reason, "why the task is being reopened");
        if (problem != null) throw new IllegalArgumentException(problem);
        move("OPEN", now);
        completedAt = null; completedBy = null; completedByName = null;
    }

    public void cancel(String reason, Instant now) {
        String problem = TaskRules.reasonProblem(reason, "why the task is being cancelled");
        if (problem != null) throw new IllegalArgumentException(problem);
        move("CANCELLED", now);
        cancelReason = reason.trim();
    }
}
