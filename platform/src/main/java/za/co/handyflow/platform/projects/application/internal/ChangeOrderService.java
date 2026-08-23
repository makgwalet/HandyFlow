package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.approvals.dto.ApprovalStepResponse;
import za.co.handyflow.platform.projects.domain.model.ChangeOrder;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectBudgetLine;
import za.co.handyflow.platform.projects.domain.repository.ChangeOrderRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectBudgetLineRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.dto.CreateChangeOrderRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FIX: backlog 1.1 — migrated onto the shared approval engine. Change
 * Order approval is genuinely single-step (no threshold, no
 * maker-checker) — every submitted CO always needs exactly one
 * PM_APPROVE approval, matching the platform-default rule seeded by
 * this migration exactly. submitChangeOrder() now also submits to the
 * engine; approveChangeOrder()/rejectChangeOrder() now act on the
 * engine's step first, and only apply this class's own existing side
 * effects (schedule extension, CONTINGENCY budget line, notification)
 * once the engine confirms the approval actually completed — all of
 * that logic is otherwise completely unchanged from before this
 * migration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeOrderService {

    private final ChangeOrderRepository       changeOrderRepo;
    private final ProjectRepository           projectRepo;
    private final ProjectBudgetLineRepository budgetRepo;
    private final SequenceService             sequenceService;
    private final PmNotificationService       notificationService;   // ← added
    // FIX: backlog 1.1 — the shared approval engine.
    private final ApprovalFacade              approvalFacade;

    private static final String APPROVALS_MODULE = "projects";
    private static final String APPROVALS_ENTITY_TYPE = "CHANGE_ORDER";

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChangeOrder> getChangeOrders(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        return changeOrderRepo.findByProject(projectId);
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    @Transactional
    public ChangeOrder createChangeOrder(TenantId tenantId, UUID projectId,
                                         CreateChangeOrderRequest req, UUID createdBy) {
        verifyProject(tenantId, projectId);
        String number = sequenceService.nextChangeOrderNumber(tenantId.getValue(), projectId);
        ChangeOrder co = ChangeOrder.create(
                tenantId.getValue(), projectId, number,
                req.title(), req.description(), req.reason(),
                req.costImpact(), req.scheduleImpact(), createdBy);
        log.info("Created change order={} number='{}' project={}", co.getId(), number, projectId);
        return changeOrderRepo.save(co);
    }

    /**
     * FIX: backlog 1.1 — now also submits to the approval engine
     * (module="projects", entityType="CHANGE_ORDER") using the CO's own
     * id as entityId. No metadata/conditions needed — the platform
     * default rule has none, matching the previous unconditional gate.
     */
    @Transactional
    public ChangeOrder submitChangeOrder(TenantId tenantId, UUID id) {
        ChangeOrder co = find(tenantId, id);
        co.submit();
        changeOrderRepo.save(co);
        approvalFacade.submit(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, id, null, Map.of());
        log.info("Submitted change order={} project={}", id, co.getProjectId());
        return co;
    }

    /**
     * Approves the change order and applies schedule + cost impacts.
     *
     * FIX: backlog 1.1 — now finds-or-creates the approval request and
     * acts on its pending step BEFORE applying any of the side effects
     * below. Since this is a genuinely single-step approval, the engine
     * confirms APPROVED in the same call almost always — the defensive
     * "not yet submitted" branch exists only for a CO that somehow
     * reaches this method without having gone through submitChangeOrder()
     * first (shouldn't normally happen — co.approve() itself still
     * requires status SUBMITTED — but this doesn't assume that
     * invariant holds and handles it explicitly rather than risk an
     * NPE on a missing request).
     * <p>
     * Everything from co.approve(...) onward — schedule extension,
     * CONTINGENCY budget line, notification — is completely unchanged
     * from before this migration.
     */
    @Transactional
    public ChangeOrder approveChangeOrder(TenantId tenantId, UUID id,
                                          UUID approverId, String approverName) {
        ChangeOrder co = find(tenantId, id);

        var existing = approvalFacade.getLatestRequestForEntity(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, id);
        ApprovalRequestResponse result;
        if (existing.isPresent() && isOpen(existing.get())) {
            ApprovalStepResponse pendingStep = firstPendingStep(existing.get())
                    .orElseThrow(() -> new HandyFlowException(
                            "This change order's approval request has no pending step — data inconsistency, needs manual review",
                            HttpStatus.CONFLICT, "NO_PENDING_STEP"));
            result = approvalFacade.actOnStep(tenantId, pendingStep.id(), approverId,
                    currentUserAuthorities(), "APPROVE", null, null);
        } else {
            result = approvalFacade.submit(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, id, approverId, Map.of());
            if (isOpen(result)) {
                ApprovalStepResponse firstStep = firstPendingStep(result)
                        .orElseThrow(() -> new IllegalStateException("A freshly-submitted request has no pending step"));
                result = approvalFacade.actOnStep(tenantId, firstStep.id(), approverId,
                        currentUserAuthorities(), "APPROVE", null, null);
            }
        }

        if (!"APPROVED".equals(result.status())) {
            // Genuinely shouldn't happen for a single-step rule, but not
            // silently assumed — surfaced as a real error rather than
            // proceeding to apply cost/schedule impacts on an approval
            // that isn't actually complete.
            throw new HandyFlowException(
                    "Approval did not complete — status: " + result.status(),
                    HttpStatus.BAD_REQUEST, "APPROVAL_NOT_COMPLETE");
        }

        co.approve(approverId, approverName);

        // Fetch project once — used for schedule extension, budget refresh, and notification
        Project project = projectRepo.findByTenantAndId(tenantId.getValue(), co.getProjectId())
                .orElseThrow(() -> new HandyFlowException(
                        "Project not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        // 1. Extend project schedule if needed
        if (co.getScheduleImpact() > 0 && project.getEndDate() != null) {
            project.setEndDate(project.getEndDate().plusDays(co.getScheduleImpact()));
            project.updateHealth();
            projectRepo.save(project);
            log.info("Extended project={} end date by {} days (CO={})",
                    project.getId(), co.getScheduleImpact(), id);
        }

        // 2. Record cost impact as a CONTINGENCY budget line (idempotent)
        if (co.getCostImpact() != null && co.getCostImpact().compareTo(BigDecimal.ZERO) > 0) {
            String description = "Change Order: " + co.getChangeNumber() + " — " + co.getTitle();
            boolean alreadyPosted = budgetRepo.findByProject(co.getProjectId()).stream()
                    .anyMatch(l -> l.getDescription().startsWith("Change Order: " + co.getChangeNumber()));
            if (!alreadyPosted) {
                int sortOrder = budgetRepo.findMaxSortOrder(co.getProjectId()) + 1;
                ProjectBudgetLine coLine = ProjectBudgetLine.create(
                        tenantId.getValue(), co.getProjectId(),
                        null, "CONTINGENCY", description,
                        co.getCostImpact(), false, false, sortOrder);
                budgetRepo.save(coLine);
                log.info("Created budget line for CO={} amount={} project={}",
                        id, co.getCostImpact(), co.getProjectId());

                project.setBudgetTotal(budgetRepo.sumBudgetedByProject(co.getProjectId()));
                project.updateHealth();
                projectRepo.save(project);
            }
        }

        ChangeOrder saved = changeOrderRepo.save(co);

        // Notify tenant admin — @Async, never blocks or rolls back this tx
        notificationService.notifyChangeOrderApproved(
                co.getTenantId(), project.getName(), co.getChangeNumber(), approverName);

        return saved;
    }

    /**
     * FIX: backlog 1.1 — routes through the engine first (REJECT
     * decision on the pending step, resolving the same "different
     * outcome, one path" reasoning already established for AP's own
     * rejection handling), then applies co.reject(reason) exactly as
     * before. Resolves the acting user internally via
     * SecurityContextHolder rather than changing this method's public
     * signature — the controller doesn't currently pass one, and
     * preserving that contract means zero controller changes.
     */
    @Transactional
    public ChangeOrder rejectChangeOrder(TenantId tenantId, UUID id, String reason) {
        ChangeOrder co = find(tenantId, id);

        var existing = approvalFacade.getLatestRequestForEntity(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, id);
        if (existing.isPresent() && isOpen(existing.get())) {
            ApprovalStepResponse pendingStep = firstPendingStep(existing.get()).orElse(null);
            if (pendingStep != null) {
                approvalFacade.actOnStep(tenantId, pendingStep.id(), currentUserId(),
                        currentUserAuthorities(), "REJECT", reason, null);
            }
        }

        co.reject(reason);
        log.info("Rejected change order={} project={}", id, co.getProjectId());
        return changeOrderRepo.save(co);
    }

    @Transactional
    public ChangeOrder markClientApproved(TenantId tenantId, UUID id) {
        ChangeOrder co = find(tenantId, id);
        co.markClientApproved();
        return changeOrderRepo.save(co);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ChangeOrder find(TenantId tenantId, UUID id) {
        return changeOrderRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException(
                        "Change order not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private void verifyProject(TenantId tenantId, UUID projectId) {
        projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> new HandyFlowException(
                        "Project not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private boolean isOpen(ApprovalRequestResponse r) {
        return "SUBMITTED".equals(r.status()) || "IN_PROGRESS".equals(r.status());
    }

    private java.util.Optional<ApprovalStepResponse> firstPendingStep(ApprovalRequestResponse r) {
        return r.steps().stream()
                .filter(s -> "PENDING".equals(s.status()))
                .min(Comparator.comparingInt(ApprovalStepResponse::stepOrder));
    }

    /** Same SecurityContextHolder pattern established in ApService/PosService for this exact purpose. */
    private List<String> currentUserAuthorities() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        try { return UUID.fromString(auth.getName()); } catch (Exception e) { return null; }
    }
}