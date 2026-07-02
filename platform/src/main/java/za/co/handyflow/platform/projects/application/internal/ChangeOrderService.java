package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeOrderService {

    private final ChangeOrderRepository       changeOrderRepo;
    private final ProjectRepository           projectRepo;
    private final ProjectBudgetLineRepository budgetRepo;
    private final SequenceService             sequenceService;
    private final PmNotificationService       notificationService;   // ← added

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

    @Transactional
    public ChangeOrder submitChangeOrder(TenantId tenantId, UUID id) {
        ChangeOrder co = find(tenantId, id);
        co.submit();
        log.info("Submitted change order={} project={}", id, co.getProjectId());
        return changeOrderRepo.save(co);
    }

    /**
     * Approves the change order and applies schedule + cost impacts.
     *
     * NEW: sends a notification email to the tenant admin after saving.
     * The notification is @Async inside PmNotificationService — it never
     * participates in this transaction and cannot cause a rollback.
     */
    @Transactional
    public ChangeOrder approveChangeOrder(TenantId tenantId, UUID id,
                                          UUID approverId, String approverName) {
        ChangeOrder co      = find(tenantId, id);
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

        // 3. Notify tenant admin — @Async, never blocks or rolls back this tx
        notificationService.notifyChangeOrderApproved(
                co.getTenantId(), project.getName(), co.getChangeNumber(), approverName);

        return saved;
    }

    @Transactional
    public ChangeOrder rejectChangeOrder(TenantId tenantId, UUID id, String reason) {
        ChangeOrder co = find(tenantId, id);
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
}