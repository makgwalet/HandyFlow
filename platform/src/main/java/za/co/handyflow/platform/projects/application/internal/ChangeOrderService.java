package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.ChangeOrder;
import za.co.handyflow.platform.projects.domain.repository.ChangeOrderRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.dto.CreateChangeOrderRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChangeOrderService {

    private final ChangeOrderRepository changeOrderRepo;
    private final ProjectRepository     projectRepo;

    @Transactional(readOnly = true)
    public List<ChangeOrder> getChangeOrders(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        return changeOrderRepo.findByProject(projectId);
    }

    @Transactional
    public ChangeOrder createChangeOrder(TenantId tenantId, UUID projectId,
                                         CreateChangeOrderRequest req, UUID createdBy) {
        verifyProject(tenantId, projectId);
        int seq = changeOrderRepo.findMaxSequence(projectId) + 1;
        String number = String.format("CO-%03d", seq);
        ChangeOrder co = ChangeOrder.create(
                tenantId.getValue(), projectId, number,
                req.title(), req.description(), req.reason(),
                req.costImpact(), req.scheduleImpact(), createdBy);
        return changeOrderRepo.save(co);
    }

    @Transactional
    public ChangeOrder submitChangeOrder(TenantId tenantId, UUID id) {
        ChangeOrder co = find(tenantId, id);
        co.submit();
        return changeOrderRepo.save(co);
    }

    @Transactional
    public ChangeOrder approveChangeOrder(TenantId tenantId, UUID id,
                                          UUID approverId, String approverName) {
        ChangeOrder co = find(tenantId, id);
        co.approve(approverId, approverName);
        // When approved, update project end date if schedule impact > 0
        if (co.getScheduleImpact() > 0) {
            projectRepo.findByTenantAndId(tenantId.getValue(), co.getProjectId())
                    .ifPresent(p -> {
                        if (p.getEndDate() != null)
                            p.setEndDate(p.getEndDate().plusDays(co.getScheduleImpact()));
                        p.updateHealth();
                        projectRepo.save(p);
                    });
        }
        return changeOrderRepo.save(co);
    }

    @Transactional
    public ChangeOrder rejectChangeOrder(TenantId tenantId, UUID id, String reason) {
        ChangeOrder co = find(tenantId, id);
        co.reject(reason);
        return changeOrderRepo.save(co);
    }

    @Transactional
    public ChangeOrder markClientApproved(TenantId tenantId, UUID id) {
        ChangeOrder co = find(tenantId, id);
        co.markClientApproved();
        return changeOrderRepo.save(co);
    }

    private ChangeOrder find(TenantId tenantId, UUID id) {
        return changeOrderRepo.findByTenantAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Change order not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private void verifyProject(TenantId tenantId, UUID projectId) {
        projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> new HandyFlowException("Project not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }
}
