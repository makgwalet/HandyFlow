package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.enums.ProjectAction;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectStatusHistory;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectStatusHistoryRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectTaskRepository;
import za.co.handyflow.platform.projects.dto.ClosureReadinessResponse;
import za.co.handyflow.platform.projects.dto.ProjectTransitionRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Single entry point for every project status change.
 *
 * WHY ONE SERVICE: all transitions go through the same validation, the same
 * audit-history write and the same transaction. Users cannot reach a status by
 * any other route — ProjectService no longer exposes a status-changing method.
 *
 * CONFIG
 *   app.projects.require-approval-gate  (default false) — when true, a project
 *       must be PROPOSED and APPROVED before it can be ACTIVATED. Default is
 *       false so existing tenants/frontends keep working; turn on per environment.
 *   app.projects.allow-self-approval    (default true)  — when false, the person
 *       who created a project cannot approve it (segregation of duties).
 *
 * Optimistic locking (Project.@Version) means two simultaneous transitions can't
 * both win: the loser gets HTTP 409 via GlobalExceptionHandler.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectLifecycleService {

    private final ProjectRepository              projectRepo;
    private final ProjectTaskRepository          taskRepo;
    private final ProjectStatusHistoryRepository historyRepo;
    private final ProjectClosureChecks           closureChecks;
    private final ScheduleBaselineService        baselineService;
    private final ProjectCostService             costService;
    private final ChangeApplicationService       changeApplicationService;
    private final DocumentService                documentService;

    @Value("${app.projects.require-approval-gate:false}")
    private boolean requireApprovalGate;

    @Value("${app.projects.allow-self-approval:true}")
    private boolean allowSelfApproval;

    // ── Transition ────────────────────────────────────────────────────────────

    @Transactional
    public Project transition(TenantId tenantId, UUID projectId, ProjectAction action,
                              ProjectTransitionRequest req, UUID userId, String userName) {
        Project p = load(tenantId, projectId);
        ProjectStatus from = p.getStatus();

        String reason         = blankToNull(req != null ? req.reason() : null);
        String closureNotes   = blankToNull(req != null ? req.closureNotes() : null);
        String lessonsLearned = blankToNull(req != null ? req.lessonsLearned() : null);

        switch (action) {
            case PROPOSE -> p.propose();

            case APPROVE -> {
                if (!allowSelfApproval && userId != null && userId.equals(p.getCreatedBy())) {
                    throw new HandyFlowException(
                            "You cannot approve a project you created",
                            HttpStatus.FORBIDDEN, "SELF_APPROVAL_NOT_ALLOWED");
                }
                p.approve(userId, userName);
            }

            case RETURN -> {
                require(reason, "A reason is required to return a project to planning");
                p.returnToPlanning();
            }

            case ACTIVATE -> {
                if (from == ProjectStatus.PLANNING && requireApprovalGate) {
                    throw new HandyFlowException(
                            "This project must be proposed and approved before it can be activated",
                            HttpStatus.CONFLICT, "APPROVAL_REQUIRED");
                }
                p.activate();
            }

            case HOLD -> p.hold();

            case COMPLETE -> {
                p.complete();
                recalculateHealth(p);
            }

            case REOPEN -> {
                require(reason, "A reason is required to reopen a project");
                p.reopen();
            }

            case CLOSE -> {
                require(lessonsLearned, "Lessons learned are required to close a project");
                ClosureReadinessResponse readiness = closureChecks.evaluate(p);
                if (!readiness.ready()) {
                    String blockers = readiness.checks().stream()
                            .filter(c -> c.blocking() && !c.passed())
                            .map(c -> c.count() + " × " + c.description().toLowerCase())
                            .reduce((a, b) -> a + "; " + b).orElse("");
                    throw new HandyFlowException(
                            "Project cannot be closed yet: " + blockers,
                            HttpStatus.CONFLICT, "CLOSURE_BLOCKED");
                }
                p.close(userId, closureNotes, lessonsLearned);
            }

            case CANCEL -> {
                require(reason, "A reason is required to cancel a project");
                p.cancel(reason);
            }
        }

        Project saved = projectRepo.save(p);
        historyRepo.save(ProjectStatusHistory.record(
                tenantId.getValue(), projectId, from, saved.getStatus(),
                action.name(), reason != null ? reason : lessonsLearned, userId, userName));

        // CHUNK 3: the project baseline just locked (see Project.approve()/activate()) —
        // capture each task's baseline dates at the same moment.
        if (action == ProjectAction.APPROVE
                || (action == ProjectAction.ACTIVATE && from == ProjectStatus.PLANNING)) {
            baselineService.snapshot(tenantId, projectId);
            costService.snapshotBaseline(tenantId, projectId);   // CHUNK 4: original budget
            changeApplicationService.recordInitialBaseline(tenantId, projectId, userId, userName);   // CHUNK 5: baseline v1
        }

        // Document retention starts the day the project is closed (nothing is deleted; see DocumentService).
        if (action == ProjectAction.CLOSE) {
            documentService.stampRetention(tenantId, projectId, LocalDate.now(ZoneId.of("Africa/Johannesburg")));
        }

        log.info("Project={} {} -> {} via {} by user={}",
                projectId, from, saved.getStatus(), action, userId);
        return saved;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ClosureReadinessResponse getClosureReadiness(TenantId tenantId, UUID projectId) {
        return closureChecks.evaluate(load(tenantId, projectId));
    }

    @Transactional(readOnly = true)
    public List<ProjectStatusHistory> getHistory(TenantId tenantId, UUID projectId) {
        load(tenantId, projectId); // tenant ownership guard
        return historyRepo.findByProject(tenantId.getValue(), projectId);
    }

    /** Lifecycle actions valid for this project right now — used to render UI buttons. */
    public List<String> availableActions(Project p) {
        List<ProjectAction> actions = switch (p.getStatus()) {
            case PLANNING  -> requireApprovalGate
                    ? List.of(ProjectAction.PROPOSE, ProjectAction.CANCEL)
                    : List.of(ProjectAction.PROPOSE, ProjectAction.ACTIVATE, ProjectAction.CANCEL);
            case PROPOSED  -> List.of(ProjectAction.APPROVE, ProjectAction.RETURN, ProjectAction.CANCEL);
            case APPROVED  -> List.of(ProjectAction.ACTIVATE, ProjectAction.CANCEL);
            case ACTIVE    -> List.of(ProjectAction.HOLD, ProjectAction.COMPLETE, ProjectAction.CANCEL);
            case ON_HOLD   -> List.of(ProjectAction.ACTIVATE, ProjectAction.CANCEL);
            case COMPLETED -> List.of(ProjectAction.CLOSE, ProjectAction.REOPEN);
            case CLOSED, CANCELLED -> List.<ProjectAction>of();
        };
        return actions.stream().map(Enum::name).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Project load(TenantId tenantId, UUID projectId) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> new HandyFlowException(
                        "Project not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    /** CHUNK 4: health only — budgetSpent now comes from the cost ledger (ProjectCostService). */
    private void recalculateHealth(Project p) {
        p.updateHealth();
    }

    private static void require(String value, String message) {
        if (value == null) {
            throw new HandyFlowException(message, HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}