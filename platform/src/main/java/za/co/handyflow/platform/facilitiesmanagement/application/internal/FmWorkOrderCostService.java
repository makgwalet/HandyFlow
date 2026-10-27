package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrderCostLine;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderCostLineRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.AddFmCostLineRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmCostLineResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Itemised cost capture (labour, parts, travel, contractor, other) — the basis for billing.
 * <ul>
 *   <li>Lines can only be added or removed while the job is ASSIGNED, IN_PROGRESS or ON_HOLD — once
 *       completed the costs are frozen; a rework request reopens them. An invoiced job can never change.</li>
 *   <li>Access follows the caller's {@link FmAccessScope} (technicians: own jobs only).</li>
 *   <li>A line can be removed by whoever recorded it, or by a MANAGE/ADMIN user.</li>
 *   <li>Every add and removal is audited — a billable cost line is a financial record.</li>
 * </ul>
 * On completion, {@link FmWorkOrderService} sets the work order's cost to the sum of its BILLABLE lines.
 * Lines marked non-billable stay for job-costing only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmWorkOrderCostService {

    private static final Set<String> EDITABLE_STATUSES = Set.of("ASSIGNED", "IN_PROGRESS", "ON_HOLD");

    private final FmWorkOrderRepository workOrderRepository;
    private final FmWorkOrderCostLineRepository costLineRepository;
    private final FmAccessPolicy accessPolicy;
    private final FmAuditService auditService;

    @Transactional(readOnly = true)
    public List<FmCostLineResponse> list(TenantId tenantId, UUID workOrderId) {
        findScoped(tenantId, workOrderId);
        return costLineRepository.findForWorkOrder(tenantId, workOrderId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public FmCostLineResponse add(TenantId tenantId, UUID workOrderId, AddFmCostLineRequest req) {
        FmWorkOrder wo = findScoped(tenantId, workOrderId);
        requireEditable(wo);

        FmWorkOrderCostLine line = FmWorkOrderCostLine.create(tenantId, workOrderId, req.costType(), req.description(),
                req.quantity(), req.unitCost(), defaultCharge(wo, req), req.billable() == null || req.billable(),
                TenantContext.getCurrentUserId());
        costLineRepository.save(line);
        auditService.record(tenantId, "COST_LINE_ADDED", "WORK_ORDER", workOrderId,
                wo.getWorkOrderNumber() + " " + line.getCostType() + " '" + line.getDescription() + "' total=" + line.getTotalCost()
                        + " charge=" + line.getTotalCharge() + " billable=" + line.isBillable());
        log.info("FM cost line added workOrder={} type={} total={} tenant={}", workOrderId, line.getCostType(), line.getTotalCost(), tenantId);
        return toResponse(line);
    }

    @Transactional
    public void remove(TenantId tenantId, UUID workOrderId, UUID lineId) {
        FmWorkOrder wo = findScoped(tenantId, workOrderId);
        requireEditable(wo);

        FmWorkOrderCostLine line = costLineRepository.findByIdForWorkOrder(tenantId, workOrderId, lineId)
                .orElseThrow(() -> new ResourceNotFoundException("FmWorkOrderCostLine", lineId.toString()));

        UUID me = TenantContext.getCurrentUserId();
        boolean privileged = accessPolicy.hasAuthority("FACILITIESMANAGEMENT_MANAGE") || accessPolicy.hasAuthority("FACILITIESMANAGEMENT_ADMIN");
        if (!privileged && (me == null || !me.equals(line.getRecordedByUserId()))) {
            throw new HandyFlowException("You can only remove cost lines you recorded", HttpStatus.FORBIDDEN, "COST_LINE_NOT_OWNED");
        }

        costLineRepository.delete(line);
        auditService.record(tenantId, "COST_LINE_REMOVED", "WORK_ORDER", workOrderId,
                wo.getWorkOrderNumber() + " " + line.getCostType() + " '" + line.getDescription() + "' total=" + line.getTotalCost()
                        + " charge=" + line.getTotalCharge() + " billable=" + line.isBillable());
    }

    /**
     * Rate-card pricing from the terms stamped on the work order when it was raised: an explicit unitCharge always
     * wins; otherwise LABOUR defaults to the agreement's hourly rate and PARTS to cost plus the agreement's markup.
     * Anything else (or no rate card) returns null, and the line then charges at cost — no markup.
     */
    private BigDecimal defaultCharge(FmWorkOrder wo, AddFmCostLineRequest req) {
        if (req.unitCharge() != null) return req.unitCharge();
        String type = req.costType() == null ? "" : req.costType().trim().toUpperCase();
        if ("LABOUR".equals(type) && wo.getLabourRate() != null) return wo.getLabourRate();
        if ("PARTS".equals(type) && wo.getPartsMarkupPercent() != null) {
            BigDecimal factor = BigDecimal.ONE.add(wo.getPartsMarkupPercent().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
            return req.unitCost().multiply(factor).setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    private void requireEditable(FmWorkOrder wo) {
        if (wo.isInvoiced()) {
            throw new IllegalStateException("This work order has already been invoiced — its costs can no longer change");
        }
        if (!EDITABLE_STATUSES.contains(wo.getStatus())) {
            throw new IllegalStateException("Costs can only be changed while the work order is ASSIGNED, IN_PROGRESS or ON_HOLD — current status: " + wo.getStatus());
        }
    }

    private FmWorkOrder findScoped(TenantId tenantId, UUID workOrderId) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        FmWorkOrder wo = workOrderRepository.findActiveById(tenantId, workOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("FmWorkOrder", workOrderId.toString()));
        if (!scope.canSeeWorkOrder(wo)) {
            throw new ResourceNotFoundException("FmWorkOrder", workOrderId.toString());
        }
        return wo;
    }

    private FmCostLineResponse toResponse(FmWorkOrderCostLine c) {
        return new FmCostLineResponse(c.getId(), c.getWorkOrderId(), c.getCostType(), c.getDescription(), c.getQuantity(),
                c.getUnitCost(), c.getTotalCost(), c.getUnitCharge(), c.getTotalCharge(), c.isBillable(),
                c.getRecordedByUserId(), c.getRecordedAt());
    }
}