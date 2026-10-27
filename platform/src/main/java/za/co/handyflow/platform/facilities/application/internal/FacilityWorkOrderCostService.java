package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityWorkOrder;
import za.co.handyflow.platform.facilities.domain.model.FacilityWorkOrderCostLine;
import za.co.handyflow.platform.facilities.domain.repository.FacilityWorkOrderCostLineRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityWorkOrderRepository;
import za.co.handyflow.platform.facilities.dto.AddCostLineRequest;
import za.co.handyflow.platform.facilities.dto.CostLineResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Itemised cost capture (labour, parts, travel, contractor, other).
 * <ul>
 *   <li>Lines can only be added or removed while the job is ASSIGNED, IN_PROGRESS or ON_HOLD — once
 *       completed the costs are frozen; a rework request reopens them.</li>
 *   <li>Access follows the caller's {@link AccessScope} (technicians: own jobs only).</li>
 *   <li>A line can be removed by whoever recorded it, or by a MANAGE/ADMIN user.</li>
 *   <li>Every add and removal is audited — cost changes are a sensitive action.</li>
 * </ul>
 * On completion, {@link FacilityWorkOrderService} sets the work order's cost to the sum of its lines.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityWorkOrderCostService {

    private static final Set<String> EDITABLE_STATUSES = Set.of("ASSIGNED", "IN_PROGRESS", "ON_HOLD");

    private final FacilityWorkOrderRepository workOrderRepository;
    private final FacilityWorkOrderCostLineRepository costLineRepository;
    private final FacilityAccessPolicy accessPolicy;
    private final FacilityAuditService auditService;

    @Transactional(readOnly = true)
    public List<CostLineResponse> list(TenantId tenantId, UUID workOrderId) {
        findScoped(tenantId, workOrderId);
        return costLineRepository.findForWorkOrder(tenantId, workOrderId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public CostLineResponse add(TenantId tenantId, UUID workOrderId, AddCostLineRequest req) {
        FacilityWorkOrder wo = findScoped(tenantId, workOrderId);
        requireEditable(wo);

        FacilityWorkOrderCostLine line = FacilityWorkOrderCostLine.create(tenantId, workOrderId, req.costType(),
                req.description(), req.quantity(), req.unitCost(), TenantContext.getCurrentUserId());
        costLineRepository.save(line);
        auditService.record(tenantId, "COST_LINE_ADDED", "WORK_ORDER", workOrderId,
                wo.getWorkOrderNumber() + " " + line.getCostType() + " '" + line.getDescription() + "' total=" + line.getTotalCost());
        log.info("Cost line added workOrder={} type={} total={} tenant={}", workOrderId, line.getCostType(), line.getTotalCost(), tenantId);
        return toResponse(line);
    }

    @Transactional
    public void remove(TenantId tenantId, UUID workOrderId, UUID lineId) {
        FacilityWorkOrder wo = findScoped(tenantId, workOrderId);
        requireEditable(wo);

        FacilityWorkOrderCostLine line = costLineRepository.findByIdForWorkOrder(tenantId, workOrderId, lineId)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityWorkOrderCostLine", lineId.toString()));

        UUID me = TenantContext.getCurrentUserId();
        boolean privileged = accessPolicy.hasAuthority("FACILITIES_MANAGE") || accessPolicy.hasAuthority("FACILITIES_ADMIN");
        if (!privileged && (me == null || !me.equals(line.getRecordedByUserId()))) {
            throw new HandyFlowException("You can only remove cost lines you recorded", HttpStatus.FORBIDDEN, "COST_LINE_NOT_OWNED");
        }

        costLineRepository.delete(line);
        auditService.record(tenantId, "COST_LINE_REMOVED", "WORK_ORDER", workOrderId,
                wo.getWorkOrderNumber() + " " + line.getCostType() + " '" + line.getDescription() + "' total=" + line.getTotalCost());
    }

    private void requireEditable(FacilityWorkOrder wo) {
        if (!EDITABLE_STATUSES.contains(wo.getStatus())) {
            throw new IllegalStateException("Costs can only be changed while the work order is ASSIGNED, IN_PROGRESS or ON_HOLD — current status: " + wo.getStatus());
        }
    }

    private FacilityWorkOrder findScoped(TenantId tenantId, UUID workOrderId) {
        AccessScope scope = accessPolicy.scope(tenantId);
        FacilityWorkOrder wo = workOrderRepository.findActiveById(tenantId, workOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityWorkOrder", workOrderId.toString()));
        if (!scope.canSeeWorkOrder(wo)) {
            throw new ResourceNotFoundException("FacilityWorkOrder", workOrderId.toString());
        }
        return wo;
    }

    private CostLineResponse toResponse(FacilityWorkOrderCostLine c) {
        return new CostLineResponse(c.getId(), c.getWorkOrderId(), c.getCostType(), c.getDescription(), c.getQuantity(),
                c.getUnitCost(), c.getTotalCost(), c.getRecordedByUserId(), c.getRecordedAt());
    }
}