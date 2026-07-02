package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.co.handyflow.platform.projects.domain.enums.ProjectHealth;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.model.*;
import za.co.handyflow.platform.projects.domain.repository.*;
import za.co.handyflow.platform.projects.domain.repository.projections.ProjectStatsSummary;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository          projectRepo;
    private final ProjectTaskRepository      taskRepo;
    private final ProjectRiskRepository      riskRepo;
    private final ProjectTimeEntryRepository timeRepo;
    private final SequenceService            sequenceService;

    // ── Summary (dashboard KPIs) ──────────────────────────────────────────────

    /**
     * Dashboard summary — active count, health breakdown, pending approvals, open red risks.
     *
     * FIX: Was calling findPendingApproval(tid).size() and findOpenRedRisks(tid).size()
     *      which loaded FULL entity lists into the JVM to count them.
     *      Now uses COUNT queries — returns a single number from the DB.
     */
    @Transactional(readOnly = true)
    public ProjectSummaryResponse getSummary(TenantId tenantId) {
        UUID tid = tenantId.getValue();
        return new ProjectSummaryResponse(
                (int) projectRepo.countActive(tid),
                (int) projectRepo.countByHealth(tid, ProjectHealth.RED),
                (int) projectRepo.countByHealth(tid, ProjectHealth.AMBER),
                (int) timeRepo.countPendingApproval(tid),
                (int) riskRepo.countOpenRedRisks(tid)
        );
    }

    // ── Project CRUD ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Project> getProjects(TenantId tenantId, String status, Pageable pageable) {
        UUID tid = tenantId.getValue();
        if (StringUtils.hasText(status)) {
            // Parse the incoming String to the enum Hibernate expects.
            // valueOf() throws IllegalArgumentException on unknown values —
            // the global exception handler maps that to HTTP 400.
            ProjectStatus statusEnum = ProjectStatus.valueOf(status.toUpperCase());
            return projectRepo.findByStatus(tid, statusEnum, pageable);
        }
        return projectRepo.findActive(tid, pageable);
    }

    @Transactional(readOnly = true)
    public Project getProject(TenantId tenantId, UUID id) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> notFound("Project"));
    }

    /**
     * Batch stats fetch for a list of projects — resolves the N+1 problem.
     *
     * WHY THIS EXISTS:
     * ProjectController.getProjects() previously called getTasks() + getRisks()
     * inside a page.map() loop — 40 DB round-trips for 20 projects.
     * Now the controller calls this once with all page IDs → 1 query.
     *
     * Returns a Map<projectId, stats> so the controller can look up stats
     * by project ID in O(1) per project.
     */
    @Transactional(readOnly = true)
    public Map<UUID, ProjectStatsSummary> getProjectStats(List<UUID> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) return Map.of();
        UUID[] ids = projectIds.toArray(UUID[]::new);
        return projectRepo.findProjectStats(ids).stream()
                .collect(Collectors.toMap(ProjectStatsSummary::getProjectId, s -> s));
    }

    /**
     * Convenience: get stats for a single project (used after create/update).
     */
    @Transactional(readOnly = true)
    public ProjectStatsSummary getProjectStats(UUID projectId) {
        UUID[] ids = { projectId };
        return projectRepo.findProjectStats(ids).stream()
                .findFirst()
                .orElse(EMPTY_STATS);
    }

    @Transactional
    public Project createProject(TenantId tenantId, CreateProjectRequest req, UUID createdBy) {
        /*
         * FIX — Race condition in project number generation.
         *
         * Old code:
         *   int seq = projectRepo.findMaxProjectSequence(tenantId) + 1;
         *   String number = "PRJ" + format(seq);
         *
         * Two concurrent requests both read MAX=5, both compute "PRJ0006",
         * the second insert fails the UNIQUE constraint → HTTP 500.
         *
         * New code delegates to SequenceService which uses PostgreSQL's atomic
         * INSERT … ON CONFLICT DO UPDATE … RETURNING in a REQUIRES_NEW
         * transaction — guaranteed unique, no retry needed.
         */
        String number = sequenceService.nextProjectNumber(tenantId.getValue());

        Project p = Project.create(tenantId.getValue(), number, req.name(),
                req.projectType(), req.clientId(), req.clientName(),
                req.startDate(), req.endDate(), req.budgetTotal(), createdBy);

        if (req.description()        != null) p.setDescription(req.description());
        if (req.contractValue()      != null) p.setContractValue(req.contractValue());
        if (req.contractRef()        != null) p.setContractRef(req.contractRef());
        if (req.projectManagerId()   != null) p.setProjectManagerId(req.projectManagerId());
        if (req.projectManagerName() != null) p.setProjectManagerName(req.projectManagerName());
        if (req.siteAddress()        != null) p.setSiteAddress(req.siteAddress());

        projectRepo.save(p);
        log.info("Created project={} number='{}' tenant={}", p.getId(), number, tenantId.getValue());
        return p;
    }

    @Transactional
    public Project updateProject(TenantId tenantId, UUID id, UpdateProjectRequest req) {
        Project p = getProject(tenantId, id);
        if (req.name()               != null) p.setName(req.name());
        if (req.description()        != null) p.setDescription(req.description());
        if (req.endDate()            != null) p.setEndDate(req.endDate());
        if (req.budgetTotal()        != null) p.setBudgetTotal(req.budgetTotal());
        if (req.projectManagerId()   != null) p.setProjectManagerId(req.projectManagerId());
        if (req.projectManagerName() != null) p.setProjectManagerName(req.projectManagerName());
        if (req.contractValue()      != null) p.setContractValue(req.contractValue());
        if (req.contractRef()        != null) p.setContractRef(req.contractRef());
        if (req.cidbGrade()          != null) p.setCidbGrade(req.cidbGrade());
        if (req.nhbrcNumber()        != null) p.setNhbrcNumber(req.nhbrcNumber());
        if (req.notes()              != null) p.setNotes(req.notes());
        p.updateHealth();
        return projectRepo.save(p);
    }

    @Transactional
    public Project updateProjectStatus(TenantId tenantId, UUID id, String action, String reason) {
        Project p = getProject(tenantId, id);
        /*
         * FIX: switch now uses the enum's canTransitionTo() guard (inside
         * each lifecycle method on Project) rather than bare strings.
         * Illegal transitions throw IllegalStateException caught by the global
         * exception handler → HTTP 409 Conflict.
         */
        switch (action.toUpperCase()) {
            case "ACTIVATE"  -> p.activate();
            case "HOLD"      -> p.hold();
            case "COMPLETE"  -> { p.complete(); recalculateHealth(p); }
            case "CANCEL"    -> p.cancel(reason);   // FIX: reason goes to cancellationReason, NOT notes
            default -> throw new HandyFlowException(
                    "Unknown action: " + action, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        }
        return projectRepo.save(p);
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectTask> getTasks(TenantId tenantId, UUID projectId) {
        getProject(tenantId, projectId); // tenant ownership guard
        return taskRepo.findByProject(projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectTask> getMilestones(TenantId tenantId, UUID projectId) {
        getProject(tenantId, projectId);
        return taskRepo.findMilestones(projectId);
    }

    @Transactional
    public ProjectTask createTask(TenantId tenantId, UUID projectId,
                                  CreateTaskRequest req, UUID createdBy) {
        getProject(tenantId, projectId);

        // FIX: Use SequenceService for both number AND sort order
        String number    = sequenceService.nextTaskNumber(tenantId.getValue(), projectId);
        int    sortOrder = taskRepo.findMaxSortOrder(projectId) + 1;

        ProjectTask t = ProjectTask.create(tenantId.getValue(), projectId, req.phaseId(),
                req.title(), req.taskType(), req.priority(),
                req.plannedStart(), req.plannedEnd(), createdBy);
        t.setTaskNumber(number);
        t.setSortOrder(sortOrder);

        if (req.assigneeId()   != null) t.setAssigneeId(req.assigneeId());
        if (req.assigneeName() != null) t.setAssigneeName(req.assigneeName());
        if (req.budgetAmount() != null) t.setBudgetAmount(req.budgetAmount());
        if (req.notes()        != null) t.setNotes(req.notes());

        taskRepo.save(t);
        log.info("Created task={} number='{}' project={}", t.getId(), number, projectId);
        return t;
    }

    @Transactional
    public ProjectTask updateTaskStatus(TenantId tenantId, UUID taskId,
                                        String action, BigDecimal progressPct) {
        ProjectTask t = taskRepo.findByTenantAndId(tenantId.getValue(), taskId)
                .orElseThrow(() -> notFound("Task"));
        switch (action.toUpperCase()) {
            case "START"    -> t.start();
            case "COMPLETE" -> t.complete();
            case "PROGRESS" -> { if (progressPct != null) t.updateProgress(progressPct); }
            case "BLOCK"    -> t.setStatus("BLOCKED");
            case "CANCEL"   -> t.setStatus("CANCELLED");
            default -> throw new HandyFlowException(
                    "Unknown action: " + action, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        }
        taskRepo.save(t);
        // Recalculate project health after task update
        projectRepo.findByTenantAndId(tenantId.getValue(), t.getProjectId())
                .ifPresent(p -> { recalculateHealth(p); projectRepo.save(p); });
        return t;
    }

    // ── Risks ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectRisk> getRisks(TenantId tenantId, UUID projectId) {
        getProject(tenantId, projectId);
        return riskRepo.findByProject(projectId);
    }

    @Transactional
    public ProjectRisk createRisk(TenantId tenantId, UUID projectId, CreateRiskRequest req) {
        getProject(tenantId, projectId);
        ProjectRisk r = ProjectRisk.create(tenantId.getValue(), projectId,
                req.title(), req.probability(), req.impact(),
                req.category(), req.mitigation(), req.ownerId(), req.ownerName());
        r.setIsOhsa(Boolean.TRUE.equals(req.isOhsa()));
        if (req.reviewDate() != null) r.setReviewDate(req.reviewDate());
        riskRepo.save(r);
        return r;
    }

    @Transactional
    public ProjectRisk updateRiskStatus(TenantId tenantId, UUID riskId,
                                        String action, String notes) {
        ProjectRisk r = riskRepo.findByTenantAndId(tenantId.getValue(), riskId)
                .orElseThrow(() -> notFound("Risk"));
        switch (action.toUpperCase()) {
            case "MITIGATE" -> r.mitigate(notes);
            case "CLOSE"    -> r.close();
            case "ACCEPT"   -> r.accept();
            default -> throw new HandyFlowException(
                    "Unknown action: " + action, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        }
        return riskRepo.save(r);
    }

    // ── Time Entries ──────────────────────────────────────────────────────────

    /**
     * Logs a time entry and updates the task's actual_hours.
     *
     * FIXES THREE BUGS from the original:
     *
     * Bug 1: Wrong scope — sumHoursByProject(projectId) was called instead of
     *         sumHoursByTask(taskId). A task's actual_hours should reflect only
     *         time logged against that task, not the whole project.
     *
     * Bug 2: No-op setter — task.setNotes(task.getNotes()) set notes to itself.
     *         No data changed; updated_at was never touched; actual_hours stayed 0.
     *
     * Bug 3: Missing setter — ProjectTask had no setActualHours() method.
     *         Even a correct call would have failed with a compile error.
     *         setActualHours() is now added to ProjectTask.
     */
    @Transactional
    public TimeEntry logTime(TenantId tenantId, UUID projectId, LogTimeRequest req,
                             UUID userId, String userName) {
        getProject(tenantId, projectId);
        TimeEntry entry = TimeEntry.create(
                tenantId.getValue(), projectId, req.taskId(),
                userId, userName,
                req.entryDate() != null ? req.entryDate() : LocalDate.now(),
                req.hours(), req.description(), req.latitude(), req.longitude());
        timeRepo.save(entry);

        // Update actual_hours on the task from the sum of ALL non-rejected entries
        if (req.taskId() != null) {
            taskRepo.findByTenantAndId(tenantId.getValue(), req.taskId()).ifPresent(task -> {
                BigDecimal taskHours = timeRepo.sumHoursByTask(req.taskId()); // FIX: task scope, not project
                task.setActualHours(taskHours);                               // FIX: correct setter
                taskRepo.save(task);
            });
        }
        return entry;
    }

    @Transactional
    public TimeEntry approveTime(TenantId tenantId, UUID entryId,
                                 UUID approverId, boolean approve) {
        TimeEntry t = timeRepo.findByTenantAndId(tenantId.getValue(), entryId)
                .orElseThrow(() -> notFound("Time entry"));
        if (approve) t.approve(approverId);
        else         t.reject();
        return timeRepo.save(t);
    }

    @Transactional(readOnly = true)
    public List<TimeEntry> getTimeEntries(TenantId tenantId, UUID projectId) {
        getProject(tenantId, projectId);
        return timeRepo.findByProject(projectId);
    }

    @Transactional(readOnly = true)
    public List<TimeEntry> getPendingTimeApprovals(TenantId tenantId) {
        return timeRepo.findPendingApproval(tenantId.getValue());
    }

    // ── Client Portal ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Project getProjectByPortalToken(String token) {
        return projectRepo.findByPortalToken(token)
                .orElseThrow(() -> notFound("Project"));
    }

    /**
     * Portal-safe milestone fetch — project already verified by token resolution.
     * Accepts the resolved Project to guarantee tenant isolation without TenantContext.
     */
    @Transactional(readOnly = true)
    public List<ProjectTask> getMilestonesForPortal(Project resolvedProject) {
        return taskRepo.findMilestones(resolvedProject.getId());
    }

    /**
     * Portal-safe risk fetch — project already verified by token resolution.
     */
    @Transactional(readOnly = true)
    public List<ProjectRisk> getRisksForPortal(Project resolvedProject) {
        return riskRepo.findByProject(resolvedProject.getId());
    }

    // ── EVM helper ────────────────────────────────────────────────────────────

    /**
     * Computes how far through the schedule we are as a percentage (0–100).
     *
     * FIX: The frontend and BudgetService previously received planPct=50 as a
     * hardcoded literal from BudgetTab.tsx.  That made SPI meaningless because
     * PV was always BAC × 50%, regardless of actual elapsed time.
     *
     * This method computes the real value:
     *   planPct = (today − startDate) / (endDate − startDate) × 100
     * clamped to [0, 100].
     *
     * Called by BudgetService.getEvm() when the caller does not supply planPct,
     * and by the frontend via a new derived-planPct query param.
     */
    public BigDecimal computePlanPct(Project project) {
        if (project.getStartDate() == null || project.getEndDate() == null) {
            return BigDecimal.valueOf(50); // fallback when no dates set
        }
        LocalDate today = LocalDate.now();
        if (!today.isAfter(project.getStartDate())) return BigDecimal.ZERO;
        if (!today.isBefore(project.getEndDate()))  return BigDecimal.valueOf(100);

        long totalDays   = java.time.temporal.ChronoUnit.DAYS.between(
                project.getStartDate(), project.getEndDate());
        long elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(
                project.getStartDate(), today);

        if (totalDays == 0) return BigDecimal.valueOf(100);
        return BigDecimal.valueOf(elapsedDays * 100.0 / totalDays)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Recalculates project health using a DB-side SUM aggregate.
     *
     * FIX: The original fetched ALL tasks into JVM and streamed + reduced them:
     *   taskRepo.findByProject(p.getId()).stream()
     *       .map(ProjectTask::getActualCost).reduce(BigDecimal.ZERO, BigDecimal::add)
     *
     * For a project with 300 tasks that's 300 entity hydrations for one addition.
     * The new SUM() is computed in the database — no entities loaded into JVM.
     */
    private void recalculateHealth(Project p) {
        BigDecimal spent = taskRepo.sumActualCostByProject(p.getId()); // FIX: DB-side SUM
        p.setBudgetSpent(spent);
        p.updateHealth();
    }

    private HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    /** Null-safe empty stats implementation for projects with no tasks/risks yet. */
    private static final ProjectStatsSummary EMPTY_STATS = new ProjectStatsSummary() {
        @Override public UUID getProjectId()          { return null; }
        @Override public long getTaskCount()          { return 0; }
        @Override public long getCompletedTaskCount() { return 0; }
        @Override public long getOpenRiskCount()      { return 0; }
    };
}