package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maintenance plans (patch E2): recurring tasks per machine by operating hours or days. A daily check (and the Check now
 * button) raises a work order for every plan that is due and has none open. The check never raises the same job twice: a
 * unique index allows one open work order per plan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarthMaintenancePlanService {
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final EarthMaintenancePlanRepository plans;
    private final EarthWorkOrderRepository workOrders;
    private final EarthAssetRepository assets;
    private final EarthWorkOrderService workOrderService;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Transactional(readOnly = true)
    public List<PlanResponse> list(TenantId tenantId, UUID assetId) {
        List<EarthMaintenancePlan> ps = assetId == null ? plans.findAllForTenant(tenantId) : plans.findForAsset(tenantId, assetId);
        Map<UUID, EarthAsset> byId = ps.isEmpty() ? Map.of() : assetsById(tenantId, ps);
        LocalDate today = LocalDate.now(ZONE);
        return ps.stream().map(p -> toResponse(p, byId.get(p.getAssetId()), today)).toList();
    }

    @Transactional
    public PlanResponse create(TenantId tenantId, CreatePlanRequest req) {
        access.require(EarthmovingRules.MAINTAIN, "set up a maintenance plan");
        EarthAsset asset = assets.findActiveById(tenantId, req.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", req.assetId().toString()));
        LocalDate today = LocalDate.now(ZONE);
        // A new plan counts from "now" unless the person says when it was last done, so it does not fire the moment it is made.
        BigDecimal baseHours = req.lastDoneHours() != null ? req.lastDoneHours() : asset.getCurrentHours();
        LocalDate baseDate = req.lastDoneDate() != null ? req.lastDoneDate() : today;
        if (baseDate.isAfter(today)) throw new IllegalArgumentException("The last-done date cannot be in the future.");
        if (baseHours != null && asset.getCurrentHours() != null && baseHours.compareTo(asset.getCurrentHours()) > 0)
            throw new IllegalArgumentException("The last-done hours (" + baseHours + ") are above the machine's current reading (" + asset.getCurrentHours() + ").");
        EarthMaintenancePlan p = EarthMaintenancePlan.create(tenantId, asset.getId(), req.name(), req.taskType(), req.triggerType(),
                req.intervalValue(), req.leadValue() == null ? 0 : req.leadValue(), baseHours, baseDate, req.priority(),
                req.blocksRelease() == null || req.blocksRelease());
        plans.save(p);
        trail.record(tenantId, "EARTHMOVING_PLAN_CREATED", "EARTHMOVING_PLAN", p.getId(), null,
                Map.of("name", p.getName(), "trigger", p.getTriggerType(), "interval", String.valueOf(p.getIntervalValue())));
        return toResponse(p, asset, today);
    }

    @Transactional
    public PlanResponse update(TenantId tenantId, UUID id, UpdatePlanRequest req) {
        access.require(EarthmovingRules.MAINTAIN, "change a maintenance plan");
        EarthMaintenancePlan p = plans.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Plan", id.toString()));
        Map<String, String> before = Map.of("name", p.getName(), "interval", String.valueOf(p.getIntervalValue()), "active", String.valueOf(p.isActive()));
        p.update(req.name(), req.intervalValue(), req.leadValue() == null ? 0 : req.leadValue(),
                req.priority() == null ? p.getPriority() : req.priority(),
                req.blocksRelease() == null ? p.isBlocksRelease() : req.blocksRelease(),
                req.active() == null ? p.isActive() : req.active());
        trail.record(tenantId, "EARTHMOVING_PLAN_UPDATED", "EARTHMOVING_PLAN", id, before,
                Map.of("name", p.getName(), "interval", String.valueOf(p.getIntervalValue()), "active", String.valueOf(p.isActive())));
        EarthAsset asset = assets.findActiveById(tenantId, p.getAssetId()).orElse(null);
        return toResponse(p, asset, LocalDate.now(ZONE));
    }

    /** The Check now button. */
    @Transactional
    public PlanScanResponse scanNow(TenantId tenantId) {
        access.require(EarthmovingRules.MAINTAIN, "check maintenance plans");
        return scan(tenantId, LocalDate.now(ZONE));
    }

    /** Raises a work order for each active plan that is due and has none open. No permission check: the nightly run uses it too. */
    @Transactional
    public PlanScanResponse scan(TenantId tenantId, LocalDate today) {
        List<EarthMaintenancePlan> active = plans.findActive(tenantId);
        Map<UUID, EarthAsset> byId = active.isEmpty() ? Map.of() : assetsById(tenantId, active);
        List<String> raised = new ArrayList<>();
        for (EarthMaintenancePlan p : active) {
            EarthAsset asset = byId.get(p.getAssetId());
            if (asset == null || asset.getStatus() == AssetStatus.RETIRED) continue;
            WorkOrderRules.PlanStatus s = p.status(asset.getCurrentHours(), today);
            if (s.state() != WorkOrderRules.PlanState.DUE && s.state() != WorkOrderRules.PlanState.OVERDUE) continue;
            if (workOrders.existsOpenForPlan(p.getId())) continue;
            String priority = s.state() == WorkOrderRules.PlanState.OVERDUE && ("LOW".equals(p.getPriority()) || "NORMAL".equals(p.getPriority()))
                    ? "HIGH" : p.getPriority();
            String unit = "HOURS".equals(p.getTriggerType()) ? "hours" : "days";
            EarthWorkOrder w = workOrderService.raise(tenantId, asset.getId(), p.getName() + " due",
                    "Planned maintenance: " + p.getName() + ". " + s.used().stripTrailingZeros().toPlainString() + " " + unit
                            + " since it was last done; the plan is every " + p.getIntervalValue() + " " + unit + ".",
                    p.getTaskType(), priority, today, "PLAN", p.getId(), null, p.isBlocksRelease(), null);
            raised.add(w.getNumber() + " " + displayName(asset) + " — " + p.getName());
        }
        if (!raised.isEmpty()) notifyRaised(tenantId, raised);
        return new PlanScanResponse(active.size(), raised.size(), raised);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<UUID, EarthAsset> assetsById(TenantId tenantId, List<EarthMaintenancePlan> ps) {
        Map<UUID, EarthAsset> m = new HashMap<>();
        for (UUID aid : ps.stream().map(EarthMaintenancePlan::getAssetId).distinct().toList())
            assets.findActiveById(tenantId, aid).ifPresent(a -> m.put(aid, a));
        return m;
    }

    private static String displayName(EarthAsset a) {
        return a.getFleetNumber() != null ? a.getFleetNumber() + " (" + a.getName() + ")" : a.getName();
    }

    private void notifyRaised(TenantId tenantId, List<String> raised) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.ASSET_SERVICE_DUE)
                .title(raised.size() + " planned maintenance job" + (raised.size() == 1 ? "" : "s") + " now due")
                .message(String.join("; ", raised.stream().limit(5).toList()) + (raised.size() > 5 ? " and " + (raised.size() - 5) + " more" : ""))
                .actionUrl("/earthmoving/workorders")
                .sourceModule("earthmoving")
                .recipients(recipients)
                .build());
    }

    PlanResponse toResponse(EarthMaintenancePlan p, EarthAsset asset, LocalDate today) {
        WorkOrderRules.PlanStatus s = p.status(asset == null ? null : asset.getCurrentHours(), today);
        return new PlanResponse(p.getId(), p.getAssetId(), p.getName(), p.getTaskType(), p.getTriggerType(), p.getIntervalValue(),
                p.getLeadValue(), p.getLastDoneHours(), p.getLastDoneDate(), p.getPriority(), p.isBlocksRelease(), p.isActive(),
                s.state().name(), s.used(), s.remaining(), "HOURS".equals(p.getTriggerType()) ? "hours" : "days");
    }
}
