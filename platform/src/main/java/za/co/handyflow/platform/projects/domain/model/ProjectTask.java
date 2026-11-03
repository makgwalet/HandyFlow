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
 * CHUNK 3 CHANGES
 * ───────────────
 * - baselineStart / baselineEnd: per-task snapshot taken when the project baseline is
 *   locked (approval or first activation). Null = the task was added after the baseline.
 * - setPlannedDates(): the one way to change dates; validates order and keeps
 *   durationDays consistent. setPlannedStart / setPlannedEnd now also keep it consistent.
 * - create() now rejects an end date before the start date (previously it silently stored
 *   a zero/negative duration).
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

    @Column(name = "baseline_start") LocalDate baselineStart;
    @Column(name = "baseline_end")   LocalDate baselineEnd;

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
        requireValidRange(plannedStart, plannedEnd);
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
        t.recomputeDuration();
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

    /** Work is done and handed to someone to check. */
    public void submitForReview() {
        if (!"IN_PROGRESS".equals(status) && !"NOT_STARTED".equals(status) && !"BLOCKED".equals(status))
            throw new IllegalStateException("Only open work can be sent for review (status: " + status + ")");
        if (actualStart == null) this.actualStart = LocalDate.now();
        this.status = "IN_REVIEW";
        touch();
    }

    /** The reviewer sends it back. */
    public void returnFromReview() {
        if (!"IN_REVIEW".equals(status))
            throw new IllegalStateException("Task is not in review (status: " + status + ")");
        this.status = "IN_PROGRESS";
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
        if (pct.compareTo(ONE_HUNDRED) == 0)   this.status = "COMPLETED";
        // progress below 100% leaves a task in review where it is; otherwise it is in progress
        else if (pct.compareTo(BigDecimal.ZERO) > 0 && !"IN_REVIEW".equals(status)) this.status = "IN_PROGRESS";
        touch();
    }

    // ── Scheduling ────────────────────────────────────────────────────────────

    /** The single way to change both planned dates at once. */
    public void setPlannedDates(LocalDate start, LocalDate end) {
        requireValidRange(start, end);
        this.plannedStart = start;
        this.plannedEnd   = end;
        recomputeDuration();
        touch();
    }

    /** Captures the current planned dates as this task's baseline (only if it has none yet). */
    public boolean snapshotBaseline() {
        if (baselineStart != null || plannedStart == null || plannedEnd == null) return false;
        this.baselineStart = plannedStart;
        this.baselineEnd   = plannedEnd;
        touch();
        return true;
    }

    /**
     * CHUNK 5: an approved change order moves this task's BASELINE dates. The before/after values
     * are kept in project_baseline_revisions, so the earlier baseline is never lost.
     */
    public void applyBaselineChange(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start))
            throw new IllegalArgumentException("Baseline end date cannot be before the baseline start date");
        this.baselineStart = start;
        this.baselineEnd   = end;
        touch();
    }

    private static void requireValidRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start))
            throw new IllegalArgumentException("Planned end date cannot be before the planned start date");
    }

    private void recomputeDuration() {
        this.durationDays = (plannedStart != null && plannedEnd != null)
                ? (int) ChronoUnit.DAYS.between(plannedStart, plannedEnd) + 1
                : null;
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setAssigneeId(UUID v)         { this.assigneeId   = v; touch(); }
    public void setAssigneeName(String v)     { this.assigneeName = v; touch(); }
    public void setPhaseId(UUID v)            { this.phaseId      = v; touch(); }
    public void setStatus(String v)           { this.status       = v; touch(); }
    public void setPlannedStart(LocalDate v)  { requireValidRange(v, plannedEnd); this.plannedStart = v; recomputeDuration(); touch(); }
    public void setPlannedEnd(LocalDate v)    { requireValidRange(plannedStart, v); this.plannedEnd = v; recomputeDuration(); touch(); }
    public void setIsCritical(boolean v)      { this.isCritical   = v; touch(); }
    public void setBudgetAmount(BigDecimal v) { this.budgetAmount = v; touch(); }
    public void setNotes(String v)            { this.notes        = v; touch(); }
    public void setTitle(String v)            { this.title        = v; touch(); }
    public void setPriority(String v)         { this.priority     = v; touch(); }
    public void setEstimatedHours(BigDecimal v) {
        if (v != null && v.signum() < 0) throw new IllegalArgumentException("Estimated hours cannot be negative");
        this.estimatedHours = v; touch();
    }
    public void setSortOrder(int v)           { this.sortOrder    = v; }

    public void setActualHours(BigDecimal v) {
        if (v != null && v.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Actual hours cannot be negative");
        this.actualHours = v != null ? v : BigDecimal.ZERO;
        touch();
    }

    public void setActualCost(BigDecimal v)  { this.actualCost  = v != null ? v : BigDecimal.ZERO; touch(); }
    public void setTaskNumber(String v)      { this.taskNumber  = v; }

    private void touch() { this.updatedAt = Instant.now(); }
}