package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine.Cascade;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine.Dep;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine.Forecast;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine.Node;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine.NodeResult;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine.Shift;
import za.co.handyflow.platform.projects.application.internal.schedule.SimpleWorkingCalendar;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectPhase;
import za.co.handyflow.platform.projects.domain.model.ProjectTask;
import za.co.handyflow.platform.projects.domain.model.TaskDependency;
import za.co.handyflow.platform.projects.domain.repository.ProjectPhaseRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectTaskRepository;
import za.co.handyflow.platform.projects.domain.repository.TaskDependencyRepository;
import za.co.handyflow.platform.projects.dto.ScheduleReportResponse;
import za.co.handyflow.platform.projects.dto.ScheduleReportResponse.PhaseRollup;
import za.co.handyflow.platform.projects.dto.ScheduleReportResponse.TaskView;
import za.co.handyflow.platform.projects.dto.TaskDatesChangeResponse;
import za.co.handyflow.platform.projects.dto.UpdateTaskDatesRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Project scheduling: read-only analysis, critical-path flags, and safe date changes.
 * All scheduling rules live in {@link ScheduleEngine}; this class only loads data, calls the
 * engine, and persists/maps the results.
 *
 * getSchedule()     — read-only forecast + variance + violations (nothing is written).
 * recalculate()     — same analysis, and persists ProjectTask.isCritical.
 * updateTaskDates() — the ONLY date-edit path. Optionally cascades dependents, and ALWAYS
 *                     reports the dependencies still broken afterwards, so a date change can
 *                     never silently invalidate the schedule.
 *
 * "Today" is evaluated in SAST (Africa/Johannesburg), matching the module's scheduled jobs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ProjectRepository        projectRepo;
    private final ProjectTaskRepository    taskRepo;
    private final TaskDependencyRepository depRepo;
    private final ProjectPhaseRepository   phaseRepo;
    private final ProjectCalendarProvider  calendars;

    // ── Read-only analysis ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ScheduleReportResponse getSchedule(TenantId tenantId, UUID projectId) {
        Project project = loadProject(tenantId, projectId);
        return buildReport(project, taskRepo.findByProject(projectId),
                depRepo.findByProject(tenantId.getValue(), projectId), LocalDate.now(SAST));
    }

    /** Runs the analysis and persists the critical-path flag on each task. Skipped if the graph has a cycle. */
    @Transactional
    public ScheduleReportResponse recalculate(TenantId tenantId, UUID projectId) {
        Project project = loadProject(tenantId, projectId);
        List<ProjectTask> tasks = taskRepo.findByProject(projectId);
        List<TaskDependency> deps = depRepo.findByProject(tenantId.getValue(), projectId);
        LocalDate today = LocalDate.now(SAST);

        ScheduleReportResponse report = buildReport(project, tasks, deps, today);
        if (report.cycleDetected()) {
            log.warn("Schedule recalculation skipped for project={} — dependency cycle", projectId);
            return report;
        }

        Map<UUID, Boolean> critical = report.tasks().stream()
                .collect(Collectors.toMap(TaskView::id, TaskView::critical));
        List<ProjectTask> changed = new ArrayList<>();
        for (ProjectTask t : tasks) {
            boolean should = critical.getOrDefault(t.getId(), false);
            if (t.isCritical() != should) {
                t.setIsCritical(should);
                changed.add(t);
            }
        }
        if (!changed.isEmpty()) taskRepo.saveAll(changed);
        log.info("Schedule recalculated project={} critical={} flagsChanged={}",
                projectId, report.criticalTaskCount(), changed.size());
        return report;
    }

    // ── Date change ───────────────────────────────────────────────────────────

    @Transactional
    public TaskDatesChangeResponse updateTaskDates(TenantId tenantId, UUID taskId, UpdateTaskDatesRequest req) {
        ProjectTask task = taskRepo.findByTenantAndId(tenantId.getValue(), taskId)
                .orElseThrow(() -> notFound("Task"));
        Project project = loadProject(tenantId, task.getProjectId());
        if (!project.getStatus().isEditable()) {
            throw new HandyFlowException("Project is " + project.getStatus() + " and can no longer be modified",
                    HttpStatus.CONFLICT, "PROJECT_NOT_EDITABLE");
        }
        if ("COMPLETED".equals(task.getStatus()) || "CANCELLED".equals(task.getStatus())) {
            throw new HandyFlowException("A " + task.getStatus() + " task cannot be rescheduled",
                    HttpStatus.CONFLICT, "TASK_NOT_RESCHEDULABLE");
        }
        if (req.plannedEnd().isBefore(req.plannedStart())) {
            throw new HandyFlowException("Planned end date cannot be before the planned start date",
                    HttpStatus.BAD_REQUEST, "INVALID_DATES");
        }

        List<ProjectTask> tasks = taskRepo.findByProject(project.getId());
        List<TaskDependency> deps = depRepo.findByProject(tenantId.getValue(), project.getId());
        Map<UUID, ProjectTask> byId = tasks.stream().collect(Collectors.toMap(ProjectTask::getId, t -> t));

        LocalDate oldStart = task.getPlannedStart(), oldEnd = task.getPlannedEnd();
        task.setPlannedDates(req.plannedStart(), req.plannedEnd());

        SimpleWorkingCalendar cal = calendarFor(tasks, LocalDate.now(SAST));
        List<Dep> engineDeps = toDeps(deps);

        List<TaskDatesChangeResponse.Shift> shifted = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        List<ProjectTask> changed = new ArrayList<>(List.of(task));

        if (req.shiftDependents()) {
            Cascade c = ScheduleEngine.cascade(toNodes(tasks), engineDeps, cal, task.getId());
            if (c.cyclic()) {
                throw new HandyFlowException("The task dependencies contain a cycle — fix them before shifting dependents",
                        HttpStatus.CONFLICT, "DEPENDENCY_CYCLE");
            }
            for (Shift s : c.shifts()) {
                ProjectTask t = byId.get(s.taskId());
                t.setPlannedDates(s.newStart(), s.newEnd());
                changed.add(t);
                shifted.add(new TaskDatesChangeResponse.Shift(t.getId(), t.getTaskNumber(),
                        s.oldStart(), s.oldEnd(), s.newStart(), s.newEnd(), s.shiftWorkingDays()));
            }
            for (UUID id : c.blocked()) blocked.add(label(byId.get(id)));
        }
        taskRepo.saveAll(changed);

        // Re-evaluate against the NEW dates (entities were mutated in place).
        List<ScheduleReportResponse.Violation> remaining = ScheduleEngine
                .violations(toNodes(tasks), engineDeps, cal).stream()
                .map(v -> toViolation(v, byId)).toList();

        log.info("Task={} dates {}→{} / {}→{}; shifted={} blocked={} remainingViolations={}",
                taskId, oldStart, req.plannedStart(), oldEnd, req.plannedEnd(),
                shifted.size(), blocked.size(), remaining.size());

        return new TaskDatesChangeResponse(
                new TaskDatesChangeResponse.Shift(task.getId(), task.getTaskNumber(),
                        oldStart, oldEnd, task.getPlannedStart(), task.getPlannedEnd(), 0),
                shifted, blocked, remaining);
    }

    // ── Report assembly ───────────────────────────────────────────────────────

    ScheduleReportResponse buildReport(Project project, List<ProjectTask> tasks,
                                       List<TaskDependency> deps, LocalDate dataDate) {
        Map<UUID, ProjectTask> byId = tasks.stream().collect(Collectors.toMap(ProjectTask::getId, t -> t));
        List<Node> nodes = toNodes(tasks);
        List<Dep> engineDeps = toDeps(deps);
        SimpleWorkingCalendar cal = calendarFor(tasks, dataDate);

        Forecast forecast = ScheduleEngine.forecast(nodes, engineDeps, cal, dataDate);
        List<ScheduleReportResponse.Violation> violations = ScheduleEngine
                .violations(nodes, engineDeps, cal).stream().map(v -> toViolation(v, byId)).toList();

        List<TaskView> views = new ArrayList<>();
        List<UUID> unscheduled = new ArrayList<>();
        int criticalCount = 0;
        LocalDate plannedStart = null, plannedFinish = null;

        for (ProjectTask t : tasks) {
            boolean cancelled = "CANCELLED".equals(t.getStatus());
            boolean hasDates = t.getPlannedStart() != null && t.getPlannedEnd() != null;
            if (!cancelled && !hasDates) unscheduled.add(t.getId());
            if (!cancelled && hasDates) {
                if (plannedStart == null || t.getPlannedStart().isBefore(plannedStart)) plannedStart = t.getPlannedStart();
                if (plannedFinish == null || t.getPlannedEnd().isAfter(plannedFinish))  plannedFinish = t.getPlannedEnd();
            }

            NodeResult r = forecast.results().get(t.getId());
            boolean critical = r != null && r.critical();
            if (critical) criticalCount++;

            LocalDate fEnd = r != null ? r.end() : null;
            LocalDate compareEnd = fEnd != null ? fEnd : t.getPlannedEnd();
            boolean overdue = !cancelled && !"COMPLETED".equals(t.getStatus())
                    && t.getPlannedEnd() != null && t.getPlannedEnd().isBefore(dataDate);
            boolean outside = hasDates && project.getStartDate() != null && project.getEndDate() != null
                    && (t.getPlannedStart().isBefore(project.getStartDate())
                    || t.getPlannedEnd().isAfter(project.getEndDate()));

            views.add(new TaskView(
                    t.getId(), t.getTaskNumber(), t.getTitle(), t.getStatus(),
                    t.getPlannedStart(), t.getPlannedEnd(), t.getBaselineStart(), t.getBaselineEnd(),
                    r != null ? r.start() : null, fEnd,
                    r != null ? r.floatDays() : null, critical, overdue,
                    days(t.getBaselineStart(), t.getPlannedStart()),
                    days(t.getBaselineEnd(), compareEnd),
                    outside));
        }

        Integer finishVariance = days(project.getBaselineEnd(), forecast.finish());

        return new ScheduleReportResponse(
                project.getId(), dataDate, plannedStart, plannedFinish,
                project.getBaselineEnd(), forecast.finish(), finishVariance,
                forecast.cyclic(), criticalCount, views, violations, unscheduled,
                rollupPhases(project.getId(), tasks, forecast));
    }

    private List<PhaseRollup> rollupPhases(UUID projectId, List<ProjectTask> tasks, Forecast forecast) {
        Map<UUID, List<ProjectTask>> byPhase = tasks.stream()
                .filter(t -> t.getPhaseId() != null && !"CANCELLED".equals(t.getStatus()))
                .collect(Collectors.groupingBy(ProjectTask::getPhaseId));

        List<PhaseRollup> out = new ArrayList<>();
        for (ProjectPhase ph : phaseRepo.findByProject(projectId)) {
            List<ProjectTask> list = byPhase.getOrDefault(ph.getId(), List.of());
            LocalDate start = null, finish = null;
            BigDecimal progressSum = BigDecimal.ZERO;
            int completed = 0;
            for (ProjectTask t : list) {
                if (t.getPlannedStart() != null && (start == null || t.getPlannedStart().isBefore(start))) start = t.getPlannedStart();
                NodeResult r = forecast.results().get(t.getId());
                LocalDate end = r != null ? r.end() : t.getPlannedEnd();
                if (end != null && (finish == null || end.isAfter(finish))) finish = end;
                progressSum = progressSum.add(t.getProgressPct() != null ? t.getProgressPct() : BigDecimal.ZERO);
                if ("COMPLETED".equals(t.getStatus())) completed++;
            }
            BigDecimal avg = list.isEmpty() ? BigDecimal.ZERO
                    : progressSum.divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP);
            boolean after = ph.getEndDate() != null && finish != null && finish.isAfter(ph.getEndDate());
            out.add(new PhaseRollup(ph.getId(), ph.getName(), list.size(), completed,
                    start, finish, avg, ph.getEndDate(), after));
        }
        return out;
    }

    // ── Mapping helpers (package-visible: DependencyService reuses them) ──────

    static List<Node> toNodes(List<ProjectTask> tasks) {
        return tasks.stream().map(t -> new Node(t.getId(), t.getPlannedStart(), t.getPlannedEnd(),
                t.getActualStart(), t.getActualEnd(), t.getStatus(), t.getProgressPct())).toList();
    }

    static List<Dep> toDeps(List<TaskDependency> deps) {
        return deps.stream().map(d -> new Dep(d.getId(), d.getPredecessorId(), d.getSuccessorId(),
                d.getDependencyType(), d.getLagDays())).toList();
    }

    static ScheduleReportResponse.Violation toViolation(ScheduleEngine.Violation v, Map<UUID, ProjectTask> byId) {
        String pred = label(byId.get(v.predecessorId()));
        String succ = label(byId.get(v.successorId()));
        String message = "%s depends on %s (%s%s) but its planned dates are %d working day(s) too early"
                .formatted(succ, pred, v.type(), v.lagDays() > 0 ? " +" + v.lagDays() : "", v.shortfallWorkingDays());
        return new ScheduleReportResponse.Violation(v.dependencyId(), v.predecessorId(), v.successorId(),
                pred, succ, v.type(), v.lagDays(), v.shortfallWorkingDays(), message);
    }

    static String label(ProjectTask t) {
        if (t == null) return "?";
        return t.getTaskNumber() != null ? t.getTaskNumber() : t.getTitle();
    }

    private SimpleWorkingCalendar calendarFor(List<ProjectTask> tasks, LocalDate dataDate) {
        LocalDate from = dataDate, to = dataDate;
        for (ProjectTask t : tasks) {
            if (t.getPlannedStart() != null && t.getPlannedStart().isBefore(from)) from = t.getPlannedStart();
            if (t.getActualStart()  != null && t.getActualStart().isBefore(from))  from = t.getActualStart();
            if (t.getPlannedEnd()   != null && t.getPlannedEnd().isAfter(to))      to = t.getPlannedEnd();
        }
        return calendars.forRange(from, to);
    }

    /** Calendar days from a to b (positive = b is later); null if either is missing. */
    private static Integer days(LocalDate a, LocalDate b) {
        return (a == null || b == null) ? null : (int) ChronoUnit.DAYS.between(a, b);
    }

    private Project loadProject(TenantId tenantId, UUID projectId) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
    }

    private static HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}