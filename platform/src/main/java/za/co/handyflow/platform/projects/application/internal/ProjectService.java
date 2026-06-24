package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.*;
import za.co.handyflow.platform.projects.domain.repository.*;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository     projectRepo;
    private final ProjectTaskRepository taskRepo;
    private final ProjectRiskRepository riskRepo;
    private final ProjectTimeEntryRepository timeRepo;

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProjectSummaryResponse getSummary(TenantId tenantId) {
        UUID tid = tenantId.getValue();
        return new ProjectSummaryResponse(
                projectRepo.countActive(tid),
                projectRepo.countByHealth(tid, "RED"),
                projectRepo.countByHealth(tid, "AMBER"),
                (long) timeRepo.findPendingApproval(tid).size(),
                riskRepo.findOpenRedRisks(tid).size()
        );
    }

    // ── Projects ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Project> getProjects(TenantId tenantId, String status, Pageable pageable) {
        UUID tid = tenantId.getValue();
        return status != null && !status.isBlank()
                ? projectRepo.findByStatus(tid, status, pageable)
                : projectRepo.findActive(tid, pageable);
    }

    @Transactional(readOnly = true)
    public Project getProject(TenantId tenantId, UUID id) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Project not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    @Transactional
    public Project createProject(TenantId tenantId, CreateProjectRequest req, UUID createdBy) {
        int seq = projectRepo.findMaxProjectSequence(tenantId.getValue()) + 1;
        String number = "PRJ" + String.format("%04d", seq);

        Project p = Project.create(tenantId.getValue(), number, req.name(),
                req.projectType(), req.clientId(), req.clientName(),
                req.startDate(), req.endDate(), req.budgetTotal(), createdBy);

        if (req.description() != null)        p.setDescription(req.description());
        if (req.contractValue() != null)      p.setContractValue(req.contractValue());
        if (req.contractRef() != null)        p.setContractRef(req.contractRef());
        if (req.projectManagerId() != null)   p.setProjectManagerId(req.projectManagerId());
        if (req.projectManagerName() != null) p.setProjectManagerName(req.projectManagerName());

        projectRepo.save(p);
        log.info("Created project={} name='{}' tenant={}", p.getId(), p.getName(), tenantId.getValue());
        return p;
    }

    @Transactional
    public Project updateProject(TenantId tenantId, UUID id, UpdateProjectRequest req) {
        Project p = getProject(tenantId, id);
        if (req.name() != null)               p.setName(req.name());
        if (req.description() != null)        p.setDescription(req.description());
        if (req.endDate() != null)            p.setEndDate(req.endDate());
        if (req.budgetTotal() != null)        p.setBudgetTotal(req.budgetTotal());
        if (req.projectManagerId() != null)   p.setProjectManagerId(req.projectManagerId());
        if (req.projectManagerName() != null) p.setProjectManagerName(req.projectManagerName());
        if (req.contractValue() != null)      p.setContractValue(req.contractValue());
        if (req.contractRef() != null)        p.setContractRef(req.contractRef());
        if (req.cidbGrade() != null)          p.setCidbGrade(req.cidbGrade());
        if (req.nhbrcNumber() != null)        p.setNhbrcNumber(req.nhbrcNumber());
        if (req.notes() != null)              p.setNotes(req.notes());
        p.updateHealth();
        return projectRepo.save(p);
    }

    @Transactional
    public Project updateProjectStatus(TenantId tenantId, UUID id, String action, String reason) {
        Project p = getProject(tenantId, id);
        switch (action.toUpperCase()) {
            case "ACTIVATE"  -> p.activate();
            case "HOLD"      -> p.hold();
            case "COMPLETE"  -> { p.complete(); recalculateHealth(p); }
            case "CANCEL"    -> p.cancel(reason);
            default -> throw new HandyFlowException("Unknown action: " + action, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        }
        return projectRepo.save(p);
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectTask> getTasks(TenantId tenantId, UUID projectId) {
        getProject(tenantId, projectId); // verify tenant ownership
        return taskRepo.findByProject(projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectTask> getMilestones(TenantId tenantId, UUID projectId) {
        getProject(tenantId, projectId);
        return taskRepo.findMilestones(projectId);
    }

    @Transactional
    public ProjectTask createTask(TenantId tenantId, UUID projectId, CreateTaskRequest req, UUID createdBy) {
        getProject(tenantId, projectId); // verify
        int seq = taskRepo.findMaxTaskSequence(projectId) + 1;
        String number = "T" + String.format("%03d", seq);

        ProjectTask t = ProjectTask.create(tenantId.getValue(), projectId, req.phaseId(),
                req.title(), req.taskType(), req.priority(),
                req.plannedStart(), req.plannedEnd(), createdBy);
        t.setSortOrder(seq);

        if (req.assigneeId() != null)   t.setAssigneeId(req.assigneeId());
        if (req.assigneeName() != null) t.setAssigneeName(req.assigneeName());
        if (req.budgetAmount() != null) t.setBudgetAmount(req.budgetAmount());
        if (req.notes() != null)        t.setNotes(req.notes());

        taskRepo.save(t);
        log.info("Created task={} project={} title='{}'", t.getId(), projectId, t.getTitle());
        return t;
    }

    @Transactional
    public ProjectTask updateTaskStatus(TenantId tenantId, UUID taskId, String action, BigDecimal progressPct) {
        ProjectTask t = taskRepo.findByTenantAndId(tenantId.getValue(), taskId)
                .orElseThrow(() -> new HandyFlowException("Task not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        switch (action.toUpperCase()) {
            case "START"    -> t.start();
            case "COMPLETE" -> t.complete();
            case "PROGRESS" -> { if (progressPct != null) t.updateProgress(progressPct); }
            case "BLOCK"    -> t.setStatus("BLOCKED");
            case "CANCEL"   -> t.setStatus("CANCELLED");
            default -> throw new HandyFlowException("Unknown action: " + action, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
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
    public ProjectRisk updateRiskStatus(TenantId tenantId, UUID riskId, String action, String notes) {
        ProjectRisk r = riskRepo.findByTenantAndId(tenantId.getValue(), riskId)
                .orElseThrow(() -> new HandyFlowException("Risk not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        switch (action.toUpperCase()) {
            case "MITIGATE" -> r.mitigate(notes);
            case "CLOSE"    -> r.close();
            case "ACCEPT"   -> r.accept();
            default -> throw new HandyFlowException("Unknown action: " + action, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        }
        return riskRepo.save(r);
    }

    // ── Time Entries ──────────────────────────────────────────────────────────

    @Transactional
    public TimeEntry logTime(TenantId tenantId, UUID projectId, LogTimeRequest req,
                             UUID userId, String userName) {
        getProject(tenantId, projectId);
        TimeEntry t = TimeEntry.create(tenantId.getValue(), projectId, req.taskId(),
                userId, userName, req.entryDate() != null ? req.entryDate() : LocalDate.now(),
                req.hours(), req.description(), req.latitude(), req.longitude());
        timeRepo.save(t);

        // Update actual hours on task
        if (req.taskId() != null) {
            taskRepo.findById(req.taskId()).ifPresent(task -> {
                // Sum all hours for this task
                BigDecimal totalHours = timeRepo.sumHoursByProject(projectId);
                task.setNotes(task.getNotes()); // trigger updatedAt
                taskRepo.save(task);
            });
        }
        return t;
    }

    @Transactional
    public TimeEntry approveTime(TenantId tenantId, UUID entryId, UUID approverId, boolean approve) {
        TimeEntry t = timeRepo.findByTenantAndId(tenantId.getValue(), entryId)
                .orElseThrow(() -> new HandyFlowException("Time entry not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
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
                .orElseThrow(() -> new HandyFlowException("Project not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    // ── Portal-safe methods (no TenantId — project already verified by token) ──

    @Transactional(readOnly = true)
    public java.util.List<ProjectTask> getMilestonesForPortal(UUID projectId) {
        return taskRepo.findMilestones(projectId);
    }

    @Transactional(readOnly = true)
    public java.util.List<ProjectRisk> getRisksForPortal(UUID projectId) {
        return riskRepo.findByProject(projectId);
    }


    // ── Helpers ───────────────────────────────────────────────────────────────

    private void recalculateHealth(Project p) {
        // Sum actual task costs into project budget_spent
        BigDecimal spent = taskRepo.findByProject(p.getId()).stream()
                .map(ProjectTask::getActualCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        p.setBudgetSpent(spent);
        p.updateHealth();
    }
}

