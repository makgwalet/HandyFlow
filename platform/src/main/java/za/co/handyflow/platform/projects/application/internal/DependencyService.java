package za.co.handyflow.platform.projects.application.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.application.internal.schedule.ScheduleEngine;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectTask;
import za.co.handyflow.platform.projects.domain.model.TaskDependency;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectTaskRepository;
import za.co.handyflow.platform.projects.domain.repository.TaskDependencyRepository;
import za.co.handyflow.platform.projects.dto.CreateDependencyRequest;
import za.co.handyflow.platform.projects.dto.DependencyCreatedResponse;
import za.co.handyflow.platform.projects.dto.DependencyResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Task dependencies — until this chunk the entity and repository existed but nothing could
 * create one through the API.
 *
 * add() rejects: tasks outside the project (or another tenant's), cancelled tasks, duplicates,
 * and — crucially — anything that would create a CYCLE (409, with the offending path named).
 * Dates that currently break the new dependency are returned as WARNINGS, not errors.
 *
 * CONCURRENCY: two people adding A→B and B→A at the same instant could each pass the cycle
 * check. A per-project PostgreSQL advisory lock, held until the transaction ends, serialises
 * dependency changes within one project so that cannot happen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DependencyService {

    private final ProjectRepository        projectRepo;
    private final ProjectTaskRepository    taskRepo;
    private final TaskDependencyRepository depRepo;
    private final ProjectReferenceValidator referenceValidator;
    private final ProjectCalendarProvider  calendars;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public List<DependencyResponse> list(TenantId tenantId, UUID projectId) {
        loadProject(tenantId, projectId);
        Map<UUID, ProjectTask> tasks = taskMap(projectId);
        return depRepo.findByProject(tenantId.getValue(), projectId).stream()
                .map(d -> toResponse(d, tasks)).toList();
    }

    @Transactional
    public DependencyCreatedResponse add(TenantId tenantId, UUID projectId, CreateDependencyRequest req) {
        Project project = loadProject(tenantId, projectId);
        requireEditable(project);
        lockProjectDependencies(projectId);

        referenceValidator.requireTaskInProject(tenantId, projectId, req.predecessorId());
        referenceValidator.requireTaskInProject(tenantId, projectId, req.successorId());

        Map<UUID, ProjectTask> tasks = taskMap(projectId);
        ProjectTask pred = tasks.get(req.predecessorId());
        ProjectTask succ = tasks.get(req.successorId());
        if ("CANCELLED".equals(pred.getStatus()) || "CANCELLED".equals(succ.getStatus())) {
            throw new HandyFlowException("Dependencies cannot involve a cancelled task",
                    HttpStatus.BAD_REQUEST, "TASK_CANCELLED");
        }

        List<TaskDependency> existing = depRepo.findByProject(tenantId.getValue(), projectId);
        if (existing.stream().anyMatch(d -> d.getPredecessorId().equals(pred.getId())
                && d.getSuccessorId().equals(succ.getId()))) {
            throw new HandyFlowException("These tasks are already linked",
                    HttpStatus.CONFLICT, "DUPLICATE_DEPENDENCY");
        }

        Optional<List<UUID>> cycle = ScheduleEngine.wouldCreateCycle(
                ScheduleService.toDeps(existing), pred.getId(), succ.getId());
        if (cycle.isPresent()) {
            String path = cycle.get().stream()
                    .map(id -> ScheduleService.label(tasks.get(id)))
                    .collect(Collectors.joining(" → "));
            throw new HandyFlowException(
                    "This dependency would create a cycle: " + ScheduleService.label(pred) + " → " + path,
                    HttpStatus.CONFLICT, "DEPENDENCY_CYCLE");
        }

        String type = req.type() == null ? "FS" : req.type().toUpperCase();
        int lag = req.lagDays() == null ? 0 : req.lagDays();
        TaskDependency dep = depRepo.save(
                TaskDependency.create(tenantId.getValue(), pred.getId(), succ.getId(), type, lag));

        // Warn (never block) if today's planned dates already break the new link.
        List<TaskDependency> all = depRepo.findByProject(tenantId.getValue(), projectId);
        List<ProjectTask> taskList = List.copyOf(tasks.values());
        LocalDate today = LocalDate.now(ScheduleService.SAST);
        List<String> warnings = ScheduleEngine.violations(
                        ScheduleService.toNodes(taskList), ScheduleService.toDeps(all), calendarFor(taskList, today))
                .stream()
                .filter(v -> dep.getId().equals(v.dependencyId()))
                .map(v -> ScheduleService.toViolation(v, tasks).message())
                .toList();

        log.info("Dependency {} {} -> {} ({}, lag {}) project={}",
                dep.getId(), ScheduleService.label(pred), ScheduleService.label(succ), type, lag, projectId);
        return new DependencyCreatedResponse(toResponse(dep, tasks), warnings);
    }

    @Transactional
    public void remove(TenantId tenantId, UUID projectId, UUID dependencyId) {
        Project project = loadProject(tenantId, projectId);
        requireEditable(project);
        lockProjectDependencies(projectId);

        TaskDependency dep = depRepo.findByTenantAndId(tenantId.getValue(), dependencyId)
                .orElseThrow(() -> notFound("Dependency"));
        // The dependency must belong to THIS project — same 404 otherwise.
        ProjectTask pred = taskRepo.findByTenantAndId(tenantId.getValue(), dep.getPredecessorId())
                .filter(t -> projectId.equals(t.getProjectId()))
                .orElseThrow(() -> notFound("Dependency"));
        depRepo.delete(dep);
        log.info("Dependency {} removed (predecessor {}) project={}", dependencyId, pred.getId(), projectId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void lockProjectDependencies(UUID projectId) {
        em.createNativeQuery("SELECT CAST(pg_advisory_xact_lock(hashtext(:k)) AS text)")
                .setParameter("k", "task-deps:" + projectId)
                .getResultList();
    }

    private Map<UUID, ProjectTask> taskMap(UUID projectId) {
        return taskRepo.findByProject(projectId).stream()
                .collect(Collectors.toMap(ProjectTask::getId, t -> t));
    }

    private za.co.handyflow.platform.projects.application.internal.schedule.SimpleWorkingCalendar
    calendarFor(List<ProjectTask> tasks, LocalDate today) {
        LocalDate from = today, to = today;
        for (ProjectTask t : tasks) {
            if (t.getPlannedStart() != null && t.getPlannedStart().isBefore(from)) from = t.getPlannedStart();
            if (t.getActualStart()  != null && t.getActualStart().isBefore(from))  from = t.getActualStart();
            if (t.getPlannedEnd()   != null && t.getPlannedEnd().isAfter(to))      to = t.getPlannedEnd();
        }
        return calendars.forRange(from, to);
    }

    private static DependencyResponse toResponse(TaskDependency d, Map<UUID, ProjectTask> tasks) {
        return new DependencyResponse(d.getId(),
                d.getPredecessorId(), ScheduleService.label(tasks.get(d.getPredecessorId())),
                d.getSuccessorId(),   ScheduleService.label(tasks.get(d.getSuccessorId())),
                d.getDependencyType(), d.getLagDays());
    }

    private Project loadProject(TenantId tenantId, UUID projectId) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
    }

    private static void requireEditable(Project p) {
        if (!p.getStatus().isEditable()) {
            throw new HandyFlowException("Project is " + p.getStatus() + " and can no longer be modified",
                    HttpStatus.CONFLICT, "PROJECT_NOT_EDITABLE");
        }
    }

    private static HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}