package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A work-breakdown-structure task inside a project.
 *
 * KEY FIX: setActualHours() was missing.
 * ─────────────────────────────────────
 * ProjectService.logTime() previously tried to update a task's actual hours
 * but there was NO setter for actualHours.  The code fell back to:
 *     task.setNotes(task.getNotes())  ← sets notes to itself — a no-op
 * So actual_hours was NEVER updated from time entries.
 * setActualHours() is now present and logTime() calls it correctly.
 */
@Entity
@Table(name = "project_tasks")
@Getter
@NoArgsConstructor
public class ProjectTask {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @Id UUID id;
    @Column(name = "tenant_id",  nullable = false) UUID   tenantId;
    @Column(name = "project_id", nullable = false) UUID   projectId;
    @Column(name = "phase_id")                     UUID   phaseId;
    @Column(name = "parent_task_id")               UUID   parentTaskId;
    @Column(name = "task_number")                  String taskNumber;
    @Column(nullable = false)                      String title;
    String description;
    @Column(name = "task_type",  nullable = false) String taskType  = "TASK";
    @Column(nullable = false)                      String status    = "NOT_STARTED";
    @Column(nullable = false)                      String priority  = "MEDIUM";

    @Column(name = "assignee_id")   UUID   assigneeId;
    @Column(name = "assignee_name") String assigneeName;

    @Column(name = "planned_start") LocalDate plannedStart;
    @Column(name = "planned_end")   LocalDate plannedEnd;
    @Column(name = "actual_start")  LocalDate actualStart;
    @Column(name = "actual_end")    LocalDate actualEnd;
    @Column(name = "duration_days") Integer   durationDays;

    @Column(name = "progress_pct",     nullable = false) BigDecimal progressPct    = BigDecimal.ZERO;
    @Column(name = "estimated_hours")                    BigDecimal estimatedHours;
    @Column(name = "actual_hours",     nullable = false) BigDecimal actualHours    = BigDecimal.ZERO;

    @Column(name = "is_critical",  nullable = false) boolean isCritical  = false;
    @Column(name = "is_milestone", nullable = false) boolean isMilestone = false;

    @Column(name = "budget_amount") BigDecimal budgetAmount;
    @Column(name = "actual_cost",   nullable = false) BigDecimal actualCost = BigDecimal.ZERO;

    @Column(name = "requires_inspection", nullable = false) boolean requiresInspection = false;
    @Column(name = "inspection_passed")                     Boolean inspectionPassed;

    @Column(name = "sort_order",  nullable = false) int sortOrder = 0;
    String notes;
    @Column(name = "created_by") UUID    createdBy;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ProjectTask create(UUID tenantId, UUID projectId, UUID phaseId,
                                     String title, String taskType, String priority,
                                     LocalDate plannedStart, LocalDate plannedEnd,
                                     UUID createdBy) {
        ProjectTask t  = new ProjectTask();
        t.id           = UUID.randomUUID();
        t.tenantId     = tenantId;
        t.projectId    = projectId;
        t.phaseId      = phaseId;
        t.title        = title;
        t.taskType     = taskType != null ? taskType : "TASK";
        t.isMilestone  = "MILESTONE".equals(t.taskType);
        t.priority     = priority  != null ? priority  : "MEDIUM";
        t.status       = "NOT_STARTED";
        t.plannedStart = plannedStart;
        t.plannedEnd   = plannedEnd;
        if (plannedStart != null && plannedEnd != null)
            t.durationDays = (int) ChronoUnit.DAYS.between(plannedStart, plannedEnd) + 1;
        t.createdBy    = createdBy;
        t.createdAt    = Instant.now();
        t.updatedAt    = Instant.now();
        return t;
    }

    // ── Lifecycle transitions ─────────────────────────────────────────────────

    public void start() {
        if (!"NOT_STARTED".equals(status) && !"BLOCKED".equals(status))
            throw new IllegalStateException("Task cannot be started from status: " + status);
        this.status      = "IN_PROGRESS";
        this.actualStart = LocalDate.now();
        touch();
    }

    public void complete() {
        this.status      = "COMPLETED";
        this.actualEnd   = LocalDate.now();
        this.progressPct = ONE_HUNDRED;
        touch();
    }

    public void updateProgress(BigDecimal pct) {
        if (pct == null)
            throw new IllegalArgumentException("Progress percentage must not be null");
        if (pct.compareTo(BigDecimal.ZERO) < 0 || pct.compareTo(ONE_HUNDRED) > 0)
            throw new IllegalArgumentException("Progress must be between 0 and 100, got: " + pct);
        this.progressPct = pct;
        // Auto-drive status from progress
        if (pct.compareTo(ONE_HUNDRED) == 0)   this.status = "COMPLETED";
        else if (pct.compareTo(BigDecimal.ZERO) > 0) this.status = "IN_PROGRESS";
        touch();
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setAssigneeId(UUID v)         { this.assigneeId   = v; touch(); }
    public void setAssigneeName(String v)     { this.assigneeName = v; touch(); }
    public void setPhaseId(UUID v)            { this.phaseId      = v; touch(); }
    public void setStatus(String v)           { this.status       = v; touch(); }
    public void setPlannedStart(LocalDate v)  { this.plannedStart = v; touch(); }
    public void setPlannedEnd(LocalDate v)    { this.plannedEnd   = v; touch(); }
    public void setIsCritical(boolean v)      { this.isCritical   = v; touch(); }
    public void setBudgetAmount(BigDecimal v) { this.budgetAmount = v; touch(); }
    public void setNotes(String v)            { this.notes        = v; touch(); }
    public void setSortOrder(int v)           { this.sortOrder    = v; }

    /**
     * Sets the actual hours logged against this task.
     *
     * WHY THIS WAS CRITICAL TO ADD:
     * logTime() in ProjectService tried to keep actual_hours current so the
     * Gantt and resource views could show real utilisation vs planned.
     * Without this setter, that update silently failed — actual_hours stayed 0
     * forever regardless of how much time was logged.
     */
    public void setActualHours(BigDecimal v) {
        if (v != null && v.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Actual hours cannot be negative");
        this.actualHours = v != null ? v : BigDecimal.ZERO;
        touch();
    }

    public void setActualCost(BigDecimal v)  { this.actualCost  = v != null ? v : BigDecimal.ZERO; touch(); }
    public void setTaskNumber(String v)      { this.taskNumber  = v; }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void touch() { this.updatedAt = Instant.now(); }
}
