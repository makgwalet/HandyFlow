package za.co.handyflow.platform.projects.application.internal;

import za.co.handyflow.platform.projects.application.internal.notify.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.co.handyflow.platform.projects.domain.enums.ProjectHealth;
import za.co.handyflow.platform.projects.domain.enums.ProjectMemberRole;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.model.*;
import za.co.handyflow.platform.projects.domain.repository.*;
import za.co.handyflow.platform.projects.domain.repository.projections.ProjectStatsSummary;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Project CRUD, tasks, risks and time entries.
 *
 * CHUNK 1 CHANGES
 * ───────────────
 * 1. updateProjectStatus() REMOVED — every status change now goes through
 *    ProjectLifecycleService so validation and audit history can't be bypassed.
 * 2. createProject() now applies every field the request carries. Previously
 *    cidbGrade, nhbrcNumber, contractType, retentionPct and notes were accepted
 *    and silently discarded.
 * 3. updateProject() now applies startDate and siteAddress (also previously
 *    ignored), the new governance fields, validates date order, refuses edits on
 *    COMPLETED / CLOSED / CANCELLED projects, and refuses start-date changes once
 *    the baseline is locked.
 * 4. createTask / createRisk / logTime / updateTaskStatus now refuse to modify a
 *    project that is no longer editable.
 * 5. logTime() verifies the task actually belongs to the project.
 *
 * CHUNK 2 CHANGES
 * ───────────────
 * - createTask() validates phaseId / parentTaskId belong to the same project.
 * - New projects get a portal-token expiry (app.projects.portal.token-ttl-days, default 365; 0 = none).
 * - getProjectByPortalToken() REMOVED: portal token resolution now lives only in
 *   ProjectPortalService, so no code path can skip the enabled/expiry checks.
 *
 * CHUNK 2b CHANGES
 * ────────────────
 * - Project list, dashboard summary and the time-approval queue are member-scoped
 *   when app.projects.restrict-to-members is on (PM_ADMIN sees everything).
 * - createProject() adds the creator (MANAGER) and any named manager / sponsor as
 *   members; updateProject() does the same when those are set.
 *
 * KNOWN, DELIBERATELY NOT CHANGED HERE (scheduled for later chunks):
 *  - (resolved in chunk 4) budgetTotal / budgetSpent / budgetCommitted are now written only by
 *    ProjectCostService from the cost ledger; a direct budgetTotal edit is refused once lines exist.
 *  - endDate can still be edited directly after the baseline is locked; this
 *    should go through change control (change-control chunk).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository          projectRepo;
    private final ProjectTaskRepository      taskRepo;
    private final ProjectRiskRepository      riskRepo;
    private final ProjectTimeEntryRepository timeRepo;
    private final SequenceService            sequenceService;
    private final ProjectReferenceValidator  referenceValidator;
    private final ProjectAccessGuard         accessGuard;
    private final ProjectScopedQueries       scopedQueries;
    private final ProjectMemberService       memberService;
    private final ProjectBudgetLineRepository budgetRepo;
    private final ProjectNotifier            notifier;
    private final ProjectActivityService     activity;

    /** Lifetime of a new project's client-portal token in days. 0 = never expires. */
    @Value("${app.projects.portal.token-ttl-days:365}")
    private int portalTokenTtlDays;

    // ── Summary (dashboard KPIs) ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProjectSummaryResponse getSummary(TenantId tenantId) {
        UUID tid = tenantId.getValue();
        UUID memberUser = accessGuard.memberScopeUserId();   // null = no scoping
        if (memberUser != null) {
            return new ProjectSummaryResponse(
                    (int) projectRepo.countActiveForMember(tid, memberUser),
                    (int) projectRepo.countByHealthForMember(tid, memberUser, ProjectHealth.RED),
                    (int) projectRepo.countByHealthForMember(tid, memberUser, ProjectHealth.AMBER),
                    (int) scopedQueries.countPendingTime(tid, memberUser),
                    (int) scopedQueries.countOpenRedRisks(tid, memberUser));
        }
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
        UUID memberUser = accessGuard.memberScopeUserId();   // null = no scoping
        if (StringUtils.hasText(status)) {
            // valueOf() throws IllegalArgumentException on unknown values —
            // the global exception handler maps that to HTTP 400.
            ProjectStatus statusEnum = ProjectStatus.valueOf(status.toUpperCase());
            return memberUser != null
                    ? projectRepo.findByStatusForMember(tid, memberUser, statusEnum, pageable)
                    : projectRepo.findByStatus(tid, statusEnum, pageable);
        }
        return memberUser != null
                ? projectRepo.findActiveForMember(tid, memberUser, pageable)
                : projectRepo.findActive(tid, pageable);
    }

    @Transactional(readOnly = true)
    public Project getProject(TenantId tenantId, UUID id) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> notFound("Project"));
    }

    /** Batch stats fetch for a list of projects — resolves the N+1 problem. */
    @Transactional(readOnly = true)
    public Map<UUID, ProjectStatsSummary> getProjectStats(List<UUID> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) return Map.of();
        UUID[] ids = projectIds.toArray(UUID[]::new);
        return projectRepo.findProjectStats(ids).stream()
                .collect(Collectors.toMap(ProjectStatsSummary::getProjectId, s -> s));
    }

    @Transactional(readOnly = true)
    public ProjectStatsSummary getProjectStats(UUID projectId) {
        UUID[] ids = { projectId };
        return projectRepo.findProjectStats(ids).stream()
                .findFirst()
                .orElse(EMPTY_STATS);
    }

    @Transactional
    public Project createProject(TenantId tenantId, CreateProjectRequest req, UUID createdBy) {
        validateDates(req.startDate(), req.endDate());

        // Atomic, race-free number via SequenceService (see its Javadoc).
        String number = sequenceService.nextProjectNumber(tenantId.getValue());

        Project p = Project.create(tenantId.getValue(), number, req.name(),
                req.projectType(), req.clientId(), req.clientName(),
                req.startDate(), req.endDate(), req.budgetTotal(), createdBy);

        // Every optional field the request carries is now actually applied.
        if (req.description()        != null) p.setDescription(blankToNull(req.description()));
        if (req.contractValue()      != null) p.setContractValue(req.contractValue());
        if (req.contractRef()        != null) p.setContractRef(blankToNull(req.contractRef()));
        if (req.contractType()       != null) p.setContractType(blankToNull(req.contractType()));
        if (req.retentionPct()       != null) p.setRetentionPct(req.retentionPct());
        if (req.cidbGrade()          != null) p.setCidbGrade(blankToNull(req.cidbGrade()));
        if (req.nhbrcNumber()        != null) p.setNhbrcNumber(blankToNull(req.nhbrcNumber()));
        if (req.projectManagerId()   != null) p.setProjectManagerId(req.projectManagerId());
        if (req.projectManagerName() != null) p.setProjectManagerName(blankToNull(req.projectManagerName()));
        if (req.siteAddress()        != null) p.setSiteAddress(blankToNull(req.siteAddress()));
        if (req.sponsorId()          != null) p.setSponsorId(req.sponsorId());
        if (req.sponsorName()        != null) p.setSponsorName(blankToNull(req.sponsorName()));
        if (req.objectives()         != null) p.setObjectives(blankToNull(req.objectives()));
        if (req.scopeStatement()     != null) p.setScopeStatement(blankToNull(req.scopeStatement()));
        if (req.businessCase()       != null) p.setBusinessCase(blankToNull(req.businessCase()));
        if (req.notes()              != null) p.setNotes(blankToNull(req.notes()));

        if (portalTokenTtlDays > 0) {
            p.setPortalTokenExpiresAt(Instant.now().plus(Duration.ofDays(portalTokenTtlDays)));
        }

        projectRepo.save(p);

        // CHUNK 2b: membership is explicit — creator and any named manager/sponsor are added.
        String actorName = TenantContext.getCurrentUserName();
        memberService.ensureMember(tenantId, p.getId(), createdBy, actorName,
                ProjectMemberRole.MANAGER, createdBy, actorName);
        memberService.ensureMember(tenantId, p.getId(), req.projectManagerId(), req.projectManagerName(),
                ProjectMemberRole.MANAGER, createdBy, actorName);
        memberService.ensureMember(tenantId, p.getId(), req.sponsorId(), req.sponsorName(),
                ProjectMemberRole.SPONSOR, createdBy, actorName);

        log.info("Created project={} number='{}' tenant={}", p.getId(), number, tenantId.getValue());
        return p;
    }

    @Transactional
    public Project updateProject(TenantId tenantId, UUID id, UpdateProjectRequest req) {
        Project p = getProject(tenantId, id);
        requireEditable(p);

        // Start date is part of the baseline: only changeable before approval.
        if (req.startDate() != null && !req.startDate().equals(p.getStartDate())
                && p.getStatus().isBaselineLocked()) {
            throw new HandyFlowException(
                    "Start date cannot be changed after the project is approved or active",
                    HttpStatus.CONFLICT, "BASELINE_LOCKED");
        }

        LocalDate newStart = req.startDate() != null ? req.startDate() : p.getStartDate();
        LocalDate newEnd   = req.endDate()   != null ? req.endDate()   : p.getEndDate();
        validateDates(newStart, newEnd);
        List<String> changed = changedFields(p, req);

        if (req.name()               != null) p.setName(req.name());
        if (req.description()        != null) p.setDescription(blankToNull(req.description()));
        if (req.startDate()          != null) p.setStartDate(req.startDate());
        if (req.endDate()            != null) p.setEndDate(req.endDate());
        // CHUNK 4: once budget lines exist, the project budget IS their sum — a direct edit would be
        // silently overwritten on the next line change. Unchanged values are ignored so a form that
        // re-sends every field still works.
        if (req.budgetTotal() != null && req.budgetTotal().compareTo(p.getBudgetTotal()) != 0) {
            if (!budgetRepo.findByProject(id).isEmpty()) {
                throw new HandyFlowException(
                        "The project budget is the sum of its budget lines — change it by revising a line or transferring between lines",
                        HttpStatus.CONFLICT, "BUDGET_MANAGED_BY_LINES");
            }
            p.setBudgetTotal(req.budgetTotal());
        }
        if (req.projectManagerId()   != null) p.setProjectManagerId(req.projectManagerId());
        if (req.projectManagerName() != null) p.setProjectManagerName(blankToNull(req.projectManagerName()));
        if (req.contractValue()      != null) p.setContractValue(req.contractValue());
        if (req.contractRef()        != null) p.setContractRef(blankToNull(req.contractRef()));
        if (req.contractType()       != null) p.setContractType(blankToNull(req.contractType()));
        if (req.retentionPct()       != null) p.setRetentionPct(req.retentionPct());
        if (req.cidbGrade()          != null) p.setCidbGrade(blankToNull(req.cidbGrade()));
        if (req.nhbrcNumber()        != null) p.setNhbrcNumber(blankToNull(req.nhbrcNumber()));
        if (req.siteAddress()        != null) p.setSiteAddress(blankToNull(req.siteAddress()));
        if (req.sponsorId()          != null) p.setSponsorId(req.sponsorId());
        if (req.sponsorName()        != null) p.setSponsorName(blankToNull(req.sponsorName()));
        if (req.objectives()         != null) p.setObjectives(blankToNull(req.objectives()));
        if (req.scopeStatement()     != null) p.setScopeStatement(blankToNull(req.scopeStatement()));
        if (req.businessCase()       != null) p.setBusinessCase(blankToNull(req.businessCase()));
        if (req.notes()              != null) p.setNotes(blankToNull(req.notes()));
        p.updateHealth();
        Project saved = projectRepo.save(p);
        if (!changed.isEmpty() && activity != null) {
            activity.record(tenantId.getValue(), id, "DETAILS_EDITED", "Project details edited: " + String.join(", ", changed), null,
                    currentUserIdOrNull(), TenantContext.getCurrentUserName());
        }

        // CHUNK 2b: a newly named manager / sponsor becomes a member (existing members keep their role).
        if (req.projectManagerId() != null || req.sponsorId() != null) {
            UUID actorId = currentUserIdOrNull();
            String actorName = TenantContext.getCurrentUserName();
            memberService.ensureMember(tenantId, id, req.projectManagerId(), req.projectManagerName(),
                    ProjectMemberRole.MANAGER, actorId, actorName);
            memberService.ensureMember(tenantId, id, req.sponsorId(), req.sponsorName(),
                    ProjectMemberRole.SPONSOR, actorId, actorName);
        }
        return saved;
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
        requireEditable(getProject(tenantId, projectId));
        // CHUNK 2: referenced phase / parent task must belong to THIS project (and tenant).
        referenceValidator.requirePhaseInProject(tenantId, projectId, req.phaseId());
        referenceValidator.requireTaskInProject(tenantId, projectId, req.parentTaskId());

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
        if (t.getAssigneeId() != null) notifyAssigned(t, createdBy);
        return t;
    }

    @Transactional
    public ProjectTask updateTaskStatus(TenantId tenantId, UUID taskId,
                                        String action, BigDecimal progressPct) {
        ProjectTask t = taskRepo.findByTenantAndId(tenantId.getValue(), taskId)
                .orElseThrow(() -> notFound("Task"));
        Project project = projectRepo.findByTenantAndId(tenantId.getValue(), t.getProjectId())
                .orElseThrow(() -> notFound("Project"));
        requireEditable(project);

        switch (action.toUpperCase()) {
            case "START"    -> t.start();
            case "COMPLETE" -> t.complete();
            case "PROGRESS" -> { if (progressPct != null) t.updateProgress(progressPct); }
            case "REVIEW"   -> {
                t.submitForReview();
                notifier.publish(ProjectEvent.toRoles(tenantId.getValue(), t.getProjectId(), PmEvent.TASK_REVIEW_REQUESTED,
                        t.getTaskNumber() + " is ready for review — " + project.getName(), t.getTitle(), t.getId(),
                        java.util.Set.of(ProjectMemberRole.MANAGER), TenantContext.getCurrentUserId()));
            }
            case "RETURN"   -> {
                t.returnFromReview();
                notifier.publish(ProjectEvent.to(tenantId.getValue(), t.getProjectId(), PmEvent.TASK_RETURNED,
                        t.getTaskNumber() + " was sent back from review — " + project.getName(), t.getTitle(), t.getId(),
                        t.getAssigneeId(), TenantContext.getCurrentUserId()));
            }
            case "BLOCK"    -> t.setStatus("BLOCKED");
            case "CANCEL"   -> t.setStatus("CANCELLED");
            default -> throw new HandyFlowException(
                    "Unknown action: " + action, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        }
        taskRepo.save(t);
        recalculateHealth(project);
        projectRepo.save(project);
        return t;
    }

    /** Title, priority, assignee (a project member), estimate and notes. */
    @Transactional
    public ProjectTask updateTaskDetails(TenantId tenantId, UUID taskId, UpdateTaskDetailsRequest req) {
        ProjectTask t = taskRepo.findByTenantAndId(tenantId.getValue(), taskId)
                .orElseThrow(() -> notFound("Task"));
        requireEditable(projectRepo.findByTenantAndId(tenantId.getValue(), t.getProjectId())
                .orElseThrow(() -> notFound("Project")));
        if (req.title() != null) {
            if (req.title().isBlank()) throw new HandyFlowException("Title is required", HttpStatus.BAD_REQUEST, "TITLE_REQUIRED");
            t.setTitle(req.title().trim());
        }
        if (req.priority() != null) t.setPriority(req.priority());
        if (Boolean.TRUE.equals(req.clearAssignee())) {
            t.setAssigneeId(null);
            t.setAssigneeName(null);
        } else if (req.assigneeId() != null) {
            ProjectMember m = memberService.find(tenantId, t.getProjectId(), req.assigneeId())
                    .orElseThrow(() -> new HandyFlowException("Only a member of the project can be assigned",
                            HttpStatus.BAD_REQUEST, "ASSIGNEE_NOT_MEMBER"));
            boolean changed = !m.getUserId().equals(t.getAssigneeId());
            t.setAssigneeId(m.getUserId());
            t.setAssigneeName(m.getUserName());
            if (changed) notifyAssigned(t, TenantContext.getCurrentUserId());
        }
        if (req.estimatedHours() != null) t.setEstimatedHours(req.estimatedHours());
        if (req.notes() != null) t.setNotes(req.notes().isBlank() ? null : req.notes().trim());
        return taskRepo.save(t);
    }

    /** Plain names of the fields this edit really changes (a form that re-sends every field changes nothing). */
    static List<String> changedFields(Project p, UpdateProjectRequest r) {
        List<String> out = new ArrayList<>();
        diff(out, "name", r.name(), p.getName());
        diff(out, "description", r.description(), p.getDescription());
        diff(out, "start date", r.startDate(), p.getStartDate());
        diff(out, "end date", r.endDate(), p.getEndDate());
        if (r.budgetTotal() != null && (p.getBudgetTotal() == null || r.budgetTotal().compareTo(p.getBudgetTotal()) != 0)) out.add("budget");
        if (r.contractValue() != null && (p.getContractValue() == null || r.contractValue().compareTo(p.getContractValue()) != 0)) out.add("contract value");
        diff(out, "contract reference", r.contractRef(), p.getContractRef());
        diff(out, "contract type", r.contractType(), p.getContractType());
        if (r.retentionPct() != null && (p.getRetentionPct() == null || r.retentionPct().compareTo(p.getRetentionPct()) != 0)) out.add("retention");
        diff(out, "CIDB grade", r.cidbGrade(), p.getCidbGrade());
        diff(out, "NHBRC number", r.nhbrcNumber(), p.getNhbrcNumber());
        diff(out, "site address", r.siteAddress(), p.getSiteAddress());
        diff(out, "project manager", r.projectManagerId(), p.getProjectManagerId());
        diff(out, "sponsor", r.sponsorId(), p.getSponsorId());
        diff(out, "objectives", r.objectives(), p.getObjectives());
        diff(out, "scope", r.scopeStatement(), p.getScopeStatement());
        diff(out, "business case", r.businessCase(), p.getBusinessCase());
        diff(out, "notes", r.notes(), p.getNotes());
        return out;
    }

    private static void diff(List<String> out, String label, Object requested, Object current) {
        if (requested == null) return;
        Object req = requested instanceof String s ? (s.isBlank() ? null : s.trim()) : requested;
        if (!java.util.Objects.equals(req, current)) out.add(label);
    }

    private void notifyAssigned(ProjectTask t, UUID actor) {
        String projectName = projectRepo.findByTenantAndId(t.getTenantId(), t.getProjectId()).map(Project::getName).orElse("a project");
        notifier.publish(ProjectEvent.to(t.getTenantId(), t.getProjectId(), PmEvent.TASK_ASSIGNED,
                t.getTaskNumber() + " is assigned to you — " + projectName,
                t.getTitle() + (t.getPlannedEnd() == null ? "" : " (due " + t.getPlannedEnd() + ")"), t.getId(), t.getAssigneeId(), actor));
    }

    // ── Risks ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectRisk> getRisks(TenantId tenantId, UUID projectId) {
        getProject(tenantId, projectId);
        return riskRepo.findByProject(projectId);
    }

    @Transactional
    public ProjectRisk createRisk(TenantId tenantId, UUID projectId, CreateRiskRequest req) {
        requireEditable(getProject(tenantId, projectId));
        ProjectRisk r = ProjectRisk.create(tenantId.getValue(), projectId,
                req.title(), req.probability(), req.impact(),
                req.category(), req.mitigation(), req.ownerId(), req.ownerName());
        r.setIsOhsa(req.isOhsa());
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
     * Logs a time entry and refreshes the task's actual_hours (task-scoped SUM).
     * Chunk 1: refuses a non-editable project and verifies the task belongs to it.
     */
    @Transactional
    public TimeEntry logTime(TenantId tenantId, UUID projectId, LogTimeRequest req,
                             UUID userId, String userName) {
        requireEditable(getProject(tenantId, projectId));

        ProjectTask task = null;
        if (req.taskId() != null) {
            task = taskRepo.findByTenantAndId(tenantId.getValue(), req.taskId())
                    .orElseThrow(() -> notFound("Task"));
            if (!projectId.equals(task.getProjectId())) {
                throw new HandyFlowException(
                        "Task does not belong to this project",
                        HttpStatus.BAD_REQUEST, "TASK_PROJECT_MISMATCH");
            }
        }

        TimeEntry entry = TimeEntry.create(
                tenantId.getValue(), projectId, req.taskId(),
                userId, userName,
                req.entryDate() != null ? req.entryDate() : LocalDate.now(),
                req.hours(), req.description(), req.latitude(), req.longitude());
        timeRepo.save(entry);

        if (task != null) {
            task.setActualHours(timeRepo.sumHoursByTask(task.getId()));
            taskRepo.save(task);
        }
        return entry;
    }

    @Transactional
    public TimeEntry approveTime(TenantId tenantId, UUID entryId,
                                 UUID approverId, boolean approve) {
        return decideTime(tenantId, entryId, approverId, approve, null);
    }

    /** Approve, or reject with a reason (required). Never your own time; decided once. */
    @Transactional
    public TimeEntry decideTime(TenantId tenantId, UUID entryId,
                                UUID approverId, boolean approve, String reason) {
        TimeEntry t = timeRepo.findByTenantAndId(tenantId.getValue(), entryId)
                .orElseThrow(() -> notFound("Time entry"));
        // the interceptor already checks the entry's project; checked again so no other caller can skip it
        accessGuard.requireView(tenantId.getValue(), t.getProjectId());
        if (!"SUBMITTED".equals(t.getStatus())) {
            throw new HandyFlowException("This time entry has already been " + t.getStatus().toLowerCase(),
                    HttpStatus.CONFLICT, "TIME_ALREADY_DECIDED");
        }
        if (approverId != null && approverId.equals(t.getUserId())) {
            throw new HandyFlowException("You cannot approve or reject your own time",
                    HttpStatus.FORBIDDEN, "TIME_SELF_APPROVAL");
        }
        if (approve) t.approve(approverId);
        else         t.reject(reason);
        TimeEntry saved = timeRepo.save(t);
        if (!approve) {
            notifier.publish(ProjectEvent.to(tenantId.getValue(), t.getProjectId(), PmEvent.TIME_REJECTED,
                    "Your time for " + t.getEntryDate() + " was rejected",
                    t.getHours() + " h" + (t.getDescription() == null ? "" : " — " + t.getDescription()) + ". Reason: " + t.getRejectionReason(),
                    t.getId(), t.getUserId(), approverId));
        }
        if (!approve && t.getTaskId() != null) {
            // rejected hours no longer count towards the task's actual hours
            taskRepo.findByTenantAndId(tenantId.getValue(), t.getTaskId()).ifPresent(task -> {
                task.setActualHours(timeRepo.sumHoursByTask(task.getId()));
                taskRepo.save(task);
            });
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TimeEntry> getTimeEntries(TenantId tenantId, UUID projectId) {
        getProject(tenantId, projectId);
        return timeRepo.findByProject(projectId);
    }

    @Transactional(readOnly = true)
    public List<TimeEntry> getPendingTimeApprovals(TenantId tenantId) {
        UUID memberUser = accessGuard.memberScopeUserId();   // null = no scoping
        return memberUser != null
                ? scopedQueries.pendingTimeEntries(tenantId.getValue(), memberUser)
                : timeRepo.findPendingApproval(tenantId.getValue());
    }

    // ── Client Portal ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectTask> getMilestonesForPortal(Project resolvedProject) {
        return taskRepo.findMilestones(resolvedProject.getId());
    }

    @Transactional(readOnly = true)
    public List<ProjectRisk> getRisksForPortal(Project resolvedProject) {
        return riskRepo.findByProject(resolvedProject.getId());
    }

    // ── EVM helper ────────────────────────────────────────────────────────────

    /** Planned % through the schedule: (today − start) / (end − start) × 100, clamped 0–100. */
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

    private static UUID currentUserIdOrNull() {
        String id = TenantContext.getUserId();
        return id != null ? UUID.fromString(id) : null;
    }

    /**
     * CHUNK 4: no longer touches budgetSpent. That figure comes from the cost ledger via
     * ProjectCostService.refreshTotals() — this method used to overwrite it from task
     * actual-cost, which nothing populates, so the two writers disagreed.
     */
    private void recalculateHealth(Project p) {
        p.updateHealth();
    }

    /** COMPLETED / CLOSED / CANCELLED projects are read-only (reopen first if needed). */
    private void requireEditable(Project p) {
        if (!p.getStatus().isEditable()) {
            throw new HandyFlowException(
                    "Project is " + p.getStatus() + " and can no longer be modified",
                    HttpStatus.CONFLICT, "PROJECT_NOT_EDITABLE");
        }
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new HandyFlowException(
                    "End date cannot be before start date",
                    HttpStatus.BAD_REQUEST, "INVALID_DATES");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private static final ProjectStatsSummary EMPTY_STATS = new ProjectStatsSummary() {
        @Override public UUID getProjectId()          { return null; }
        @Override public long getTaskCount()          { return 0; }
        @Override public long getCompletedTaskCount() { return 0; }
        @Override public long getOpenRiskCount()      { return 0; }
    };
}