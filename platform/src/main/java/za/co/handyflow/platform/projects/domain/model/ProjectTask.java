package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project_tasks")
@Getter
@NoArgsConstructor
public class ProjectTask {

    @Id UUID id;
    @Column(name = "tenant_id",  nullable = false) UUID   tenantId;
    @Column(name = "project_id", nullable = false) UUID   projectId;
    @Column(name = "phase_id")                     UUID   phaseId;
    @Column(name = "parent_task_id")               UUID   parentTaskId;
    @Column(name = "task_number")                  String taskNumber;
    @Column(nullable = false)                      String title;
    String description;
    @Column(name = "task_type", nullable = false)  String taskType  = "TASK";
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

    @Column(name = "sort_order", nullable = false) int sortOrder = 0;
    String notes;
    @Column(name = "created_by") UUID    createdBy;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static ProjectTask create(UUID tenantId, UUID projectId, UUID phaseId,
                                     String title, String taskType, String priority,
                                     LocalDate plannedStart, LocalDate plannedEnd,
                                     UUID createdBy) {
        ProjectTask t = new ProjectTask();
        t.id           = UUID.randomUUID();
        t.tenantId     = tenantId;
        t.projectId    = projectId;
        t.phaseId      = phaseId;
        t.title        = title;
        t.taskType     = taskType != null ? taskType : "TASK";
        t.isMilestone  = "MILESTONE".equals(t.taskType);
        t.priority     = priority != null ? priority : "MEDIUM";
        t.status       = "NOT_STARTED";
        t.plannedStart = plannedStart;
        t.plannedEnd   = plannedEnd;
        if (plannedStart != null && plannedEnd != null)
            t.durationDays = (int) java.time.temporal.ChronoUnit.DAYS.between(plannedStart, plannedEnd) + 1;
        t.createdBy    = createdBy;
        t.createdAt    = Instant.now();
        t.updatedAt    = Instant.now();
        return t;
    }

    public void start() {
        if (!"NOT_STARTED".equals(status) && !"BLOCKED".equals(status))
            throw new IllegalStateException("Task cannot be started from " + status);
        this.status      = "IN_PROGRESS";
        this.actualStart = LocalDate.now();
        touch();
    }

    public void complete() {
        this.status      = "COMPLETED";
        this.actualEnd   = LocalDate.now();
        this.progressPct = new BigDecimal("100");
        touch();
    }

    public void updateProgress(BigDecimal pct) {
        if (pct.compareTo(BigDecimal.ZERO) < 0 || pct.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("Progress must be 0–100");
        this.progressPct = pct;
        if (pct.compareTo(new BigDecimal("100")) == 0) this.status = "COMPLETED";
        else if (pct.compareTo(BigDecimal.ZERO) > 0)   this.status = "IN_PROGRESS";
        touch();
    }

    public void setAssigneeId(UUID v)          { this.assigneeId   = v; }
    public void setAssigneeName(String v)      { this.assigneeName = v; }
    public void setPhaseId(UUID v)             { this.phaseId      = v; }
    public void setStatus(String v)            { this.status       = v; touch(); }
    public void setPlannedStart(LocalDate v)   { this.plannedStart = v; }
    public void setPlannedEnd(LocalDate v)     { this.plannedEnd   = v; }
    public void setIsCritical(boolean v)       { this.isCritical   = v; }
    public void setBudgetAmount(BigDecimal v)  { this.budgetAmount = v; }
    public void setNotes(String v)             { this.notes        = v; }
    public void setSortOrder(int v)            { this.sortOrder    = v; }

    private void touch() { this.updatedAt = Instant.now(); }
}
