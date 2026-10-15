package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Work orders (patch E2): request, assign, start, wait for parts, complete or cancel. Completing one writes the maintenance
 * record, so cost, hours and supplier land in the asset history, and moves the maintenance plan on. Completing a job does
 * NOT release the machine: release is its own action with its own authority (decision 3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarthWorkOrderService {
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final EarthWorkOrderRepository workOrders;
    private final EarthWorkOrderLineRepository lines;
    private final EarthAssetRepository assets;
    private final EarthMaintenancePlanRepository plans;
    private final EarthAssetService assetService;
    private final EarthmovingNumbering numbering;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;
    private final UserRecipientResolver users;

    // ── reading ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<WorkOrderResponse> list(TenantId tenantId, String status, UUID assetId, Pageable pageable) {
        Page<EarthWorkOrder> page;
        if (assetId != null) page = workOrders.findByAsset(tenantId, assetId, pageable);
        else if (status != null && !status.isBlank()) page = workOrders.findByStatus(tenantId, parseStatus(status), pageable);
        else page = workOrders.findAllForTenant(tenantId, pageable);
        return page.map(w -> toResponse(w, List.of()));
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse get(TenantId tenantId, UUID id) {
        EarthWorkOrder w = find(tenantId, id);
        return toResponse(w, lines.findForWorkOrder(tenantId, id));
    }

    // ── creating ─────────────────────────────────────────────────────────────

    @Transactional
    public WorkOrderResponse create(TenantId tenantId, CreateWorkOrderRequest req, UUID actor) {
        access.require(EarthmovingRules.WRITE, "request a work order");
        EarthAsset asset = assets.findActiveById(tenantId, req.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", req.assetId().toString()));
        LocalDate today = LocalDate.now(ZONE);
        WorkOrderRules.checkCreate(req.title(), req.type(), req.priority(), req.dueDate(), today);
        if (asset.getStatus() == AssetStatus.RETIRED) throw new IllegalStateException("A retired machine cannot take a work order.");

        EarthWorkOrder w = raise(tenantId, asset.getId(), req.title(), req.description(), req.type(), req.priority(), req.dueDate(),
                "MANUAL", null, req.incidentId(), req.blocksRelease() == null || req.blocksRelease(), actor);
        if (req.assignedToUserId() != null) {
            access.require(EarthmovingRules.MAINTAIN, "assign a work order");
            Recipient who = resolveUser(tenantId, req.assignedToUserId());
            w.assign(who.userId(), who.displayName());
        }
        return toResponse(w, List.of());
    }

    /** Raises a work order with no permission check: for the nightly plan check and for incidents the caller already vetted. */
    @Transactional
    public EarthWorkOrder raise(TenantId tenantId, UUID assetId, String title, String description, String type, String priority,
                                LocalDate due, String source, UUID planId, UUID incidentId, boolean blocksRelease, UUID requestedBy) {
        String number = numbering.next(tenantId, "WO", "WO");
        EarthWorkOrder w = EarthWorkOrder.create(tenantId, assetId, number, title, description, type, priority, due, source,
                planId, incidentId, blocksRelease, requestedBy);
        workOrders.save(w);
        trail.record(tenantId, "EARTHMOVING_WORK_ORDER_RAISED", "EARTHMOVING_WORK_ORDER", w.getId(), null,
                Map.of("number", number, "title", w.getTitle(), "type", w.getType(), "source", source,
                        "blocksRelease", String.valueOf(blocksRelease)));
        log.info("Work order raised {} asset={} source={}", number, assetId, source);
        return w;
    }

    /** A breakdown or accident raises its repair job straight away, so release cannot happen before the repair is recorded. */
    @Transactional
    public EarthWorkOrder raiseFromIncident(TenantId tenantId, UUID assetId, UUID incidentId, String title, String severity, UUID actor) {
        String priority = "CRITICAL".equals(severity) ? "URGENT" : "HIGH";
        return raise(tenantId, assetId, "Repair: " + title, null, "REPAIR", priority, LocalDate.now(ZONE), "INCIDENT", null,
                incidentId, true, actor);
    }

    // ── moving a job along ───────────────────────────────────────────────────

    @Transactional
    public WorkOrderResponse assign(TenantId tenantId, UUID id, UUID userId) {
        access.require(EarthmovingRules.MAINTAIN, "assign a work order");
        EarthWorkOrder w = find(tenantId, id);
        Recipient who = resolveUser(tenantId, userId);
        w.assign(who.userId(), who.displayName());
        trail.record(tenantId, "EARTHMOVING_WORK_ORDER_ASSIGNED", "EARTHMOVING_WORK_ORDER", id, null,
                Map.of("number", w.getNumber(), "assignedTo", who.displayName()));
        return toResponse(w, lines.findForWorkOrder(tenantId, id));
    }

    @Transactional
    public WorkOrderResponse start(TenantId tenantId, UUID id) {
        access.require(EarthmovingRules.MAINTAIN, "start a work order");
        EarthWorkOrder w = find(tenantId, id);
        w.start();
        trail.record(tenantId, "EARTHMOVING_WORK_ORDER_STARTED", "EARTHMOVING_WORK_ORDER", id, null, Map.of("number", w.getNumber()));
        return toResponse(w, lines.findForWorkOrder(tenantId, id));
    }

    @Transactional
    public WorkOrderResponse awaitParts(TenantId tenantId, UUID id) {
        access.require(EarthmovingRules.MAINTAIN, "update a work order");
        EarthWorkOrder w = find(tenantId, id);
        w.awaitParts();
        trail.record(tenantId, "EARTHMOVING_WORK_ORDER_AWAITING_PARTS", "EARTHMOVING_WORK_ORDER", id, null, Map.of("number", w.getNumber()));
        return toResponse(w, lines.findForWorkOrder(tenantId, id));
    }

    @Transactional
    public WorkOrderResponse resume(TenantId tenantId, UUID id) {
        access.require(EarthmovingRules.MAINTAIN, "update a work order");
        EarthWorkOrder w = find(tenantId, id);
        w.resume();
        trail.record(tenantId, "EARTHMOVING_WORK_ORDER_RESUMED", "EARTHMOVING_WORK_ORDER", id, null, Map.of("number", w.getNumber()));
        return toResponse(w, lines.findForWorkOrder(tenantId, id));
    }

    @Transactional
    public WorkOrderResponse cancel(TenantId tenantId, UUID id, String reason) {
        access.require(EarthmovingRules.MAINTAIN, "cancel a work order");
        EarthWorkOrder w = find(tenantId, id);
        w.cancel(reason);
        trail.record(tenantId, "EARTHMOVING_WORK_ORDER_CANCELLED", "EARTHMOVING_WORK_ORDER", id, null,
                Map.of("number", w.getNumber(), "reason", w.getCancelReason()));
        return toResponse(w, lines.findForWorkOrder(tenantId, id));
    }

    /** Turning blocking off lets the machine be released with this job still open; it needs a reason and is audited. */
    @Transactional
    public WorkOrderResponse setBlocksRelease(TenantId tenantId, UUID id, boolean value, String reason) {
        access.require(EarthmovingRules.MAINTAIN, "change a work order");
        EarthWorkOrder w = find(tenantId, id);
        String why = value ? null : EarthmovingRules.requireReason(reason, "letting a machine be released with this job open");
        boolean before = w.isBlocksRelease();
        w.setBlocksRelease(value);
        trail.record(tenantId, "EARTHMOVING_WORK_ORDER_BLOCKING_CHANGED", "EARTHMOVING_WORK_ORDER", id,
                Map.of("blocksRelease", String.valueOf(before)),
                why == null ? Map.of("blocksRelease", String.valueOf(value)) : Map.of("blocksRelease", String.valueOf(value), "reason", why));
        return toResponse(w, lines.findForWorkOrder(tenantId, id));
    }

    // ── parts and labour ─────────────────────────────────────────────────────

    @Transactional
    public WorkOrderResponse addLine(TenantId tenantId, UUID id, AddWorkOrderLineRequest req) {
        access.require(EarthmovingRules.MAINTAIN, "add a cost to a work order");
        EarthWorkOrder w = find(tenantId, id);
        if (!w.getStatus().isOpen()) throw new IllegalStateException("Costs cannot be added to a " + w.getStatus().name().toLowerCase() + " work order.");
        lines.save(EarthWorkOrderLine.create(tenantId, id, req.kind(), req.description(), req.quantity(), req.unitCost(), req.supplier()));
        return toResponse(w, lines.findForWorkOrder(tenantId, id));
    }

    @Transactional
    public WorkOrderResponse removeLine(TenantId tenantId, UUID id, UUID lineId) {
        access.require(EarthmovingRules.MAINTAIN, "remove a cost from a work order");
        EarthWorkOrder w = find(tenantId, id);
        if (!w.getStatus().isOpen()) throw new IllegalStateException("Costs cannot be removed from a " + w.getStatus().name().toLowerCase() + " work order.");
        EarthWorkOrderLine line = lines.findForWorkOrder(tenantId, id).stream().filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Work order line", lineId.toString()));
        lines.delete(line);
        return toResponse(w, lines.findForWorkOrder(tenantId, id));
    }

    // ── completing ───────────────────────────────────────────────────────────

    /**
     * Records the work done and the test result, writes the maintenance record (so cost and hours reach the asset history)
     * and restarts the plan's counting. The machine stays in its current status: releasing it is a separate step.
     */
    @Transactional
    public WorkOrderResponse complete(TenantId tenantId, UUID id, CompleteWorkOrderRequest req, UUID actor) {
        access.require(EarthmovingRules.MAINTAIN, "complete a work order");
        EarthWorkOrder w = find(tenantId, id);
        if (w.getStatus() != WorkOrderStatus.IN_PROGRESS)
            throw new IllegalStateException("Start the work order before completing it (it is " + w.getStatus().name().toLowerCase().replace('_', ' ') + ").");
        WorkOrderRules.checkComplete(w.getType(), req.workDone(), req.testResult(), req.meterHours(), req.downtimeHours(), req.labourHours());

        List<EarthWorkOrderLine> costLines = lines.findForWorkOrder(tenantId, id);
        BigDecimal total = costLines.stream().map(EarthWorkOrderLine::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        String supplier = costLines.stream().map(EarthWorkOrderLine::getSupplier).filter(s -> s != null).findFirst().orElse(null);

        MaintenanceResponse record = assetService.recordMaintenance(tenantId, w.getAssetId(), new CreateMaintenanceRequest(
                w.getType(), w.getNumber() + " " + w.getTitle() + ": " + req.workDone().trim(), Instant.now(),
                req.meterHours(), total.signum() == 0 ? null : total, supplier, null));
        w.complete(actor, req.workDone(), req.testResult(), req.meterHours(), req.downtimeHours(), req.labourHours(), record.id());

        if (w.getPlanId() != null) {
            plans.findByTenantAndId(tenantId, w.getPlanId()).ifPresent(p -> {
                BigDecimal hours = req.meterHours() != null ? req.meterHours()
                        : assets.findActiveById(tenantId, w.getAssetId()).map(EarthAsset::getCurrentHours).orElse(null);
                p.markDone(hours, LocalDate.now(ZONE));
            });
        }
        trail.record(tenantId, "EARTHMOVING_WORK_ORDER_COMPLETED", "EARTHMOVING_WORK_ORDER", id, null,
                Map.of("number", w.getNumber(), "cost", total.toPlainString(), "testResult", w.getTestResult() == null ? "" : w.getTestResult()));
        return toResponse(w, costLines);
    }

    /** Open work orders that stop this machine being released. */
    @Transactional(readOnly = true)
    public List<EarthWorkOrder> openBlocking(TenantId tenantId, UUID assetId) { return workOrders.findOpenBlocking(tenantId, assetId); }

    // ── helpers ──────────────────────────────────────────────────────────────

    private EarthWorkOrder find(TenantId tenantId, UUID id) {
        return workOrders.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Work order", id.toString()));
    }

    private Recipient resolveUser(TenantId tenantId, UUID userId) {
        return users.resolveUser(tenantId, userId)
                .orElseThrow(() -> new IllegalArgumentException("That person is not a user of this company."));
    }

    private static WorkOrderStatus parseStatus(String raw) {
        try { return WorkOrderStatus.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown work order status: " + raw); }
    }

    WorkOrderResponse toResponse(EarthWorkOrder w, List<EarthWorkOrderLine> costLines) {
        List<WorkOrderLineResponse> ls = costLines.stream().map(l -> new WorkOrderLineResponse(l.getId(), l.getKind(),
                l.getDescription(), l.getQuantity(), l.getUnitCost(), l.total(), l.getSupplier())).toList();
        BigDecimal total = ls.stream().map(WorkOrderLineResponse::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WorkOrderResponse(w.getId(), w.getAssetId(), w.getNumber(), w.getTitle(), w.getDescription(), w.getType(),
                w.getPriority(), w.getStatus().name(), w.getAssignedToUserId(), w.getAssignedToName(), w.getDueDate(),
                w.isOverdue(LocalDate.now(ZONE)), w.getSource(), w.getPlanId(), w.getIncidentId(), w.isBlocksRelease(),
                w.getStartedAt(), w.getCompletedAt(), w.getMeterHours(), w.getDowntimeHours(), w.getLabourHours(), w.getWorkDone(),
                w.getTestResult(), w.getMaintenanceRecordId(), w.getCancelReason(), total, ls, w.getCreatedAt());
    }
}
