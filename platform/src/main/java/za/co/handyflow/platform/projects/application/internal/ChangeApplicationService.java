package za.co.handyflow.platform.projects.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.application.internal.ProjectCostService.AppliedAllocation;
import za.co.handyflow.platform.projects.application.internal.change.ChangeEffectEngine;
import za.co.handyflow.platform.projects.application.internal.change.ChangeEffectEngine.ScheduleShift;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine.Cascade;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine.Shift;
import za.co.handyflow.platform.projects.application.internal.schedule.SimpleWorkingCalendar;
import za.co.handyflow.platform.projects.domain.model.*;
import za.co.handyflow.platform.projects.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Applies an APPROVED change order to the project — budget, dates, tasks — and records a new
 * baseline version describing exactly what moved.
 *
 * Runs inside the approval transaction: if any step fails (e.g. a reduction would take a line
 * below what is already spent), the whole approval rolls back — nothing is half-applied.
 *
 * WHAT CHANGES
 *  - COST: each cost allocation adjusts an existing budget line or creates a new one, each with a
 *    budget revision linked to the change order (ProjectCostService). A change order with no
 *    allocations and a positive cost impact uses the legacy form: one new CONTINGENCY-category line.
 *  - SCHEDULE: the project end date AND the approved baseline end both move by scheduleImpact days —
 *    before this chunk only endDate moved, so an approved delay still made the project look late
 *    against its own baseline.
 *  - TASKS: each affected task's end is extended by its delay (working days); dependents are pushed
 *    out with the same cascade the scheduler uses; baseline dates of everything that moved shift by
 *    the same working days, so an approved change shows no slippage against the baseline.
 *    Completed tasks are never moved; started tasks keep their start.
 *  - HISTORY: project.baselineVersion increments and a project_baseline_revisions row stores the old
 *    and new values of everything above. Version 1 (the original baseline) is recorded when the
 *    baseline first locks, so every revision since is recoverable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeApplicationService {

    private final ProjectRepository                 projectRepo;
    private final ProjectTaskRepository             taskRepo;
    private final TaskDependencyRepository          depRepo;
    private final ProjectBudgetLineRepository       lineRepo;
    private final ChangeOrderCostLineRepository     costLineRepo;
    private final ChangeOrderTaskRepository         coTaskRepo;
    private final ProjectBaselineRevisionRepository baselineRepo;
    private final ProjectCostService                costService;
    private final ProjectCalendarProvider           calendars;
    private final ObjectMapper                      objectMapper;

    // ── Version 1 ─────────────────────────────────────────────────────────────

    /** Records the baseline as first locked (version 1). Idempotent. Called by the lifecycle service. */
    @Transactional
    public void recordInitialBaseline(TenantId tenantId, UUID projectId, UUID by, String byName) {
        Project project = projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
        if (baselineRepo.existsByProjectAndVersion(projectId, 1)) return;
        if (project.getBaselineVersion() == 0) project.setBaselineVersion(1);

        BigDecimal originalBudget = lineRepo.findByProject(projectId).stream()
                .map(l -> l.getOriginalAmount() != null ? l.getOriginalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long baselinedTasks = taskRepo.findByProject(projectId).stream()
                .filter(t -> t.getBaselineStart() != null).count();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("baselineStart", project.getBaselineStart());
        detail.put("baselineEnd", project.getBaselineEnd());
        detail.put("originalBudget", originalBudget);
        detail.put("baselinedTasks", baselinedTasks);
        baselineRepo.save(ProjectBaselineRevision.record(tenantId.getValue(), projectId, 1, null,
                "Initial baseline", json(detail), by, byName));
        projectRepo.save(project);
    }

    // ── Apply ─────────────────────────────────────────────────────────────────

    /** Returns the new baseline version. */
    @Transactional
    public int apply(TenantId tenantId, ChangeOrder co, Project project, UUID by, String byName) {
        UUID projectId = project.getId();
        if (project.getBaselineVersion() == 0) recordInitialBaselineFor(tenantId, project, by, byName);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("changeNumber", co.getChangeNumber());
        detail.put("title", co.getTitle());

        // ── Cost ──
        List<Map<String, Object>> costChanges = new ArrayList<>();
        List<ChangeOrderCostLine> lines = costLineRepo.findByChangeOrder(co.getId());
        if (!lines.isEmpty()) {
            for (ChangeOrderCostLine l : lines) {
                costChanges.add(applyOne(tenantId, project, co, l.getBudgetLineId(), l.getCategory(),
                        l.getDescription(), l.getAmount(), by, byName));
            }
        } else if (co.getCostImpact().signum() > 0) {                 // legacy single-line form
            costChanges.add(applyOne(tenantId, project, co, null, "CONTINGENCY", co.getTitle(),
                    co.getCostImpact(), by, byName));
        }
        if (!costChanges.isEmpty()) {
            costService.refreshTotals(project);
            detail.put("budgetLines", costChanges);
        }

        // ── Project dates ──
        if (co.getScheduleImpact() != 0) {
            LocalDate oldEnd = project.getEndDate(), oldBaselineEnd = project.getBaselineEnd();
            ScheduleShift shift;
            try {
                shift = ChangeEffectEngine.shiftEnd(oldEnd, oldBaselineEnd, project.getStartDate(), co.getScheduleImpact());
            } catch (IllegalArgumentException e) {
                throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_SCHEDULE_IMPACT");
            }
            project.applyApprovedSchedule(shift.newEnd(), shift.newBaselineEnd());
            project.updateHealth();
            Map<String, Object> dates = new LinkedHashMap<>();
            dates.put("days", co.getScheduleImpact());
            dates.put("endDate", Map.of("old", String.valueOf(oldEnd), "new", String.valueOf(shift.newEnd())));
            dates.put("baselineEnd", Map.of("old", String.valueOf(oldBaselineEnd), "new", String.valueOf(shift.newBaselineEnd())));
            detail.put("projectDates", dates);
        }

        // ── Tasks ──
        List<Map<String, Object>> taskChanges = applyTaskDelays(tenantId, project, co);
        if (!taskChanges.isEmpty()) detail.put("tasks", taskChanges);

        // ── New baseline version ──
        int version = project.getBaselineVersion() + 1;
        project.setBaselineVersion(version);
        baselineRepo.save(ProjectBaselineRevision.record(tenantId.getValue(), projectId, version, co.getId(),
                "Change order " + co.getChangeNumber() + " — " + co.getTitle(), json(detail), by, byName));
        projectRepo.save(project);
        co.markApplied(version);

        log.info("Change order {} applied to project={} → baseline v{} (cost lines={}, task changes={})",
                co.getChangeNumber(), projectId, version, costChanges.size(), taskChanges.size());
        return version;
    }

    @Transactional(readOnly = true)
    public List<ProjectBaselineRevision> history(TenantId tenantId, UUID projectId) {
        projectRepo.findByTenantAndId(tenantId.getValue(), projectId).orElseThrow(() -> notFound("Project"));
        return baselineRepo.findByProject(tenantId.getValue(), projectId);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Map<String, Object> applyOne(TenantId tenantId, Project project, ChangeOrder co, UUID lineId,
                                         String category, String description, BigDecimal amount,
                                         UUID by, String byName) {
        AppliedAllocation a = costService.applyChangeOrderAllocation(tenantId, project, co.getId(),
                co.getChangeNumber(), lineId, category, description, amount, by, byName);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("budgetLineId", a.lineId());
        m.put("created", a.created());
        m.put("old", a.oldAmount());
        m.put("new", a.newAmount());
        return m;
    }

    private List<Map<String, Object>> applyTaskDelays(TenantId tenantId, Project project, ChangeOrder co) {
        List<ChangeOrderTask> links = coTaskRepo.findByChangeOrder(co.getId());
        if (links.isEmpty()) return List.of();

        List<ProjectTask> tasks = taskRepo.findByProject(project.getId());
        Map<UUID, ProjectTask> byId = tasks.stream().collect(Collectors.toMap(ProjectTask::getId, t -> t));
        List<TaskDependency> deps = depRepo.findByProject(tenantId.getValue(), project.getId());
        SimpleWorkingCalendar cal = calendarFor(tasks);

        List<Map<String, Object>> changes = new ArrayList<>();
        Set<ProjectTask> touched = new LinkedHashSet<>();

        for (ChangeOrderTask link : links) {
            ProjectTask t = byId.get(link.getTaskId());
            if (t == null || link.getDelayDays() <= 0 || t.getPlannedEnd() == null
                    || "COMPLETED".equals(t.getStatus()) || "CANCELLED".equals(t.getStatus())) continue;

            LocalDate oldStart = t.getPlannedStart(), oldEnd = t.getPlannedEnd();
            LocalDate oldBStart = t.getBaselineStart(), oldBEnd = t.getBaselineEnd();
            LocalDate newEnd = cal.plusWorkingDays(cal.rollBack(oldEnd), link.getDelayDays());
            t.setPlannedDates(oldStart, newEnd);
            if (oldBStart != null && oldBEnd != null) {
                t.applyBaselineChange(oldBStart, cal.plusWorkingDays(cal.rollBack(oldBEnd), link.getDelayDays()));
            }
            touched.add(t);
            changes.add(taskChange(t, "DELAYED", oldStart, oldEnd, oldBStart, oldBEnd, link.getDelayDays()));

            // Dependents slide out behind it; their baselines move by the same working days.
            Cascade c = ScheduleEngine.cascade(ScheduleService.toNodes(tasks), ScheduleService.toDeps(deps), cal, t.getId());
            if (c.cyclic()) continue;
            for (Shift s : c.shifts()) {
                ProjectTask d = byId.get(s.taskId());
                LocalDate dBStart = d.getBaselineStart(), dBEnd = d.getBaselineEnd();
                d.setPlannedDates(s.newStart(), s.newEnd());
                if (dBStart != null && dBEnd != null) {
                    d.applyBaselineChange(cal.plusWorkingDays(dBStart, s.shiftWorkingDays()),
                            cal.plusWorkingDays(cal.rollBack(dBEnd), s.shiftWorkingDays()));
                }
                touched.add(d);
                changes.add(taskChange(d, "PUSHED", s.oldStart(), s.oldEnd(), dBStart, dBEnd, s.shiftWorkingDays()));
            }
        }
        if (!touched.isEmpty()) taskRepo.saveAll(touched);
        return changes;
    }

    private static Map<String, Object> taskChange(ProjectTask t, String kind, LocalDate oldStart, LocalDate oldEnd,
                                                  LocalDate oldBStart, LocalDate oldBEnd, int workingDays) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", t.getId());
        m.put("taskNumber", t.getTaskNumber());
        m.put("kind", kind);
        m.put("workingDays", workingDays);
        m.put("planned", Map.of("oldStart", String.valueOf(oldStart), "oldEnd", String.valueOf(oldEnd),
                "newStart", String.valueOf(t.getPlannedStart()), "newEnd", String.valueOf(t.getPlannedEnd())));
        if (oldBStart != null || oldBEnd != null) {
            m.put("baseline", Map.of("oldStart", String.valueOf(oldBStart), "oldEnd", String.valueOf(oldBEnd),
                    "newStart", String.valueOf(t.getBaselineStart()), "newEnd", String.valueOf(t.getBaselineEnd())));
        }
        return m;
    }

    /** Used when a change order is the first thing to touch a legacy project that has no recorded baseline. */
    private void recordInitialBaselineFor(TenantId tenantId, Project project, UUID by, String byName) {
        project.setBaselineVersion(1);
        if (!baselineRepo.existsByProjectAndVersion(project.getId(), 1)) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("baselineStart", project.getBaselineStart());
            detail.put("baselineEnd", project.getBaselineEnd());
            detail.put("note", "Recorded when the first change order was applied; reflects the baseline just before it");
            baselineRepo.save(ProjectBaselineRevision.record(tenantId.getValue(), project.getId(), 1, null,
                    "Initial baseline (recorded at first change order)", json(detail), by, byName));
        }
    }

    private SimpleWorkingCalendar calendarFor(List<ProjectTask> tasks) {
        LocalDate from = LocalDate.now(ScheduleService.SAST), to = from;
        for (ProjectTask t : tasks) {
            if (t.getPlannedStart() != null && t.getPlannedStart().isBefore(from)) from = t.getPlannedStart();
            if (t.getBaselineStart() != null && t.getBaselineStart().isBefore(from)) from = t.getBaselineStart();
            if (t.getPlannedEnd() != null && t.getPlannedEnd().isAfter(to)) to = t.getPlannedEnd();
            if (t.getBaselineEnd() != null && t.getBaselineEnd().isAfter(to)) to = t.getBaselineEnd();
        }
        return calendars.forRange(from, to);
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise baseline detail", e);
        }
    }

    private static HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}