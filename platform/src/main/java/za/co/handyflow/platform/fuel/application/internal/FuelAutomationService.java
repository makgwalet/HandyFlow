package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelAutomationLog;
import za.co.handyflow.platform.fuel.domain.model.FuelAutomationRule;
import za.co.handyflow.platform.fuel.domain.model.FuelPurchaseOrder;
import za.co.handyflow.platform.fuel.domain.model.FuelSupplier;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.AutomationDtos.*;
import za.co.handyflow.platform.fuel.dto.RequisitionDtos.RequisitionRequest;
import za.co.handyflow.platform.fuel.dto.RequisitionDtos.RequisitionView;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Phase 6b: automation the company sets up itself. A rule drafts a requisition when a tank is below the level the company chose.
 * What it never does: submit, approve or order. A rule skips a tank that already has a requisition moving or an order on the way,
 * so it cannot pile up drafts. An order raised without a requisition is not linked to a tank, so the rule cannot see it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelAutomationService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelAutomationRuleRepository rules;
    private final FuelAutomationLogRepository logs;
    private final FuelTankRepository tanks;
    private final FuelSupplierRepository suppliers;
    private final FuelPurchaseRequisitionRepository requisitions;
    private final FuelPurchaseOrderRepository orders;
    private final FuelRequisitionService requisitionService;
    private final FuelSettingsService settings;

    @Transactional(readOnly = true)
    public List<RuleView> list(TenantId tenantId) {
        Map<UUID, FuelTank> tankById = new HashMap<>();
        for (FuelTank t : tanks.findAllActive(tenantId)) tankById.put(t.getId(), t);
        Map<UUID, String> supplierNames = new HashMap<>();
        for (FuelSupplier s : suppliers.findAllActive(tenantId)) supplierNames.put(s.getId(), s.getName());
        return rules.findAllActive(tenantId).stream().map(r -> view(r, tankById.get(r.getTankId()), supplierNames)).toList();
    }

    @Transactional
    public RuleView create(TenantId tenantId, RuleRequest req, UUID by) {
        FuelTank tank = tanks.findActiveById(tenantId, req.tankId() == null ? UUID.randomUUID() : req.tankId())
                .orElseThrow(() -> new ResourceNotFoundException("Tank", String.valueOf(req.tankId())));
        if (rules.findActiveByTank(tenantId, tank.getId()).isPresent()) throw new ConflictException("This tank already has a rule. Edit that one instead.");
        requireSupplier(tenantId, req.supplierId());
        FuelAutomationRule r = FuelAutomationRule.create(tenantId, tank.getId(), req.supplierId(), req.reorderBelowLitres(), req.orderLitres(), req.estimatedPrice(), by);
        if (req.enabled() != null && !req.enabled()) r.update(req.supplierId(), req.reorderBelowLitres(), req.orderLitres(), req.estimatedPrice(), false);
        return one(tenantId, rules.save(r));
    }

    @Transactional
    public RuleView update(TenantId tenantId, UUID id, RuleRequest req) {
        FuelAutomationRule r = find(tenantId, id);
        requireSupplier(tenantId, req.supplierId());
        r.update(req.supplierId(), req.reorderBelowLitres(), req.orderLitres(), req.estimatedPrice(), req.enabled() == null ? r.isEnabled() : req.enabled());
        return one(tenantId, rules.save(r));
    }

    @Transactional
    public void remove(TenantId tenantId, UUID id) {
        FuelAutomationRule r = find(tenantId, id);
        r.softDelete();
        rules.save(r);
    }

    @Transactional(readOnly = true)
    public List<LogView> log(TenantId tenantId) {
        Map<UUID, String> tankNames = new HashMap<>();
        for (FuelTank t : tanks.findAllActive(tenantId)) tankNames.put(t.getId(), t.getName());
        Map<UUID, UUID> ruleTank = new HashMap<>();
        for (FuelAutomationRule r : rules.findAllActive(tenantId)) ruleTank.put(r.getId(), r.getTankId());
        return logs.findRecent(tenantId, PageRequest.of(0, 50)).stream()
                .map(l -> new LogView(l.getId(), l.getRuleId(), tankNames.get(ruleTank.get(l.getRuleId())), l.getOutcome(), l.getDetail(), l.getRequisitionId(), l.getRanAt())).toList();
    }

    /**
     * Checks every enabled rule once. Each rule is handled on its own, so one failure does not stop the rest. Not transactional as a whole:
     * a draft that was made stays made even if a later rule fails, and a draft whose log line could not be saved still blocks a second
     * draft on the next run because the tank then has an open requisition.
     */
    public RunResult runRules(TenantId tenantId) {
        int checked = 0, drafted = 0, open = 0, failed = 0;
        List<String> numbers = new ArrayList<>();
        for (FuelAutomationRule rule : rules.findEnabled(tenantId)) {
            checked++;
            try {
                FuelTank tank = tanks.findActiveById(tenantId, rule.getTankId()).orElse(null);
                rule.recordChecked();
                if (tank == null || !rule.isBelow(tank.getCurrentLitres())) { rules.save(rule); continue; }
                if (requisitions.countOpenForTank(tenantId, tank.getId()) > 0 || requisitions.countOnOrderForTank(tenantId, tank.getId()) > 0) {
                    open++; rules.save(rule); continue;
                }
                String reason = "Automatic: " + tank.getName() + " is at " + tank.getCurrentLitres().stripTrailingZeros().toPlainString()
                        + " L, below the reorder level of " + rule.getReorderBelowLitres().stripTrailingZeros().toPlainString() + " L.";
                RequisitionView v = requisitionService.createFromRule(tenantId, new RequisitionRequest(tank.getFuelType(), rule.getOrderLitres(), tank.getId(),
                        rule.getSupplierId(), rule.getEstimatedPrice(), null, reason), rule.getId());
                rule.recordDrafted(); rules.save(rule);
                logs.save(FuelAutomationLog.drafted(tenantId, rule.getId(), v.reqNumber() + " drafted for " + tank.getName() + ". " + reason, v.id()));
                drafted++; numbers.add(v.reqNumber());
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Automation rule {} failed for tenant {}: {}", rule.getId(), tenantId, ex.getMessage());
                try { logs.save(FuelAutomationLog.failed(tenantId, rule.getId(), ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())); }
                catch (RuntimeException ignored) { log.warn("Could not record the failure of rule {}", rule.getId()); }
            }
        }
        return new RunResult(checked, drafted, open, failed, numbers);
    }

    /** Orders past their expected date that nobody has been told about yet. Nothing is marked here: the caller marks after the notice is sent. */
    @Transactional(readOnly = true)
    public List<FuelPurchaseOrder> overdueOrders(TenantId tenantId) {
        return orders.findOverdueNotAlerted(tenantId, LocalDate.now(SAST));
    }

    @Transactional
    public void markOverdueAlerted(TenantId tenantId, Collection<UUID> orderIds) {
        for (UUID id : orderIds) {
            orders.findActiveById(tenantId, id).ifPresent(p -> { p.markOverdueAlerted(); orders.save(p); });
        }
    }

    @Transactional(readOnly = true)
    public AutomationSettings settings(TenantId tenantId) { return new AutomationSettings(settings.current(tenantId).isAlertOverduePurchaseOrders()); }

    @Transactional
    public AutomationSettings updateSettings(TenantId tenantId, boolean alertOverdue) { return settings.updateAutomation(tenantId, alertOverdue); }

    private void requireSupplier(TenantId tenantId, UUID supplierId) {
        if (supplierId != null && suppliers.findActiveById(tenantId, supplierId).isEmpty()) throw new ResourceNotFoundException("Supplier", supplierId.toString());
    }

    private FuelAutomationRule find(TenantId tenantId, UUID id) {
        return rules.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Automation rule", id.toString()));
    }

    private RuleView one(TenantId tenantId, FuelAutomationRule r) {
        Map<UUID, String> names = new HashMap<>();
        if (r.getSupplierId() != null) suppliers.findActiveById(tenantId, r.getSupplierId()).ifPresent(s -> names.put(s.getId(), s.getName()));
        return view(r, tanks.findActiveById(tenantId, r.getTankId()).orElse(null), names);
    }

    private static RuleView view(FuelAutomationRule r, FuelTank t, Map<UUID, String> supplierNames) {
        return new RuleView(r.getId(), r.getTankId(), t == null ? "(tank removed)" : t.getName(), t == null ? null : t.getFuelType(), t == null ? null : t.getCurrentLitres(),
                r.getSupplierId(), r.getSupplierId() == null ? null : supplierNames.get(r.getSupplierId()), r.getReorderBelowLitres(), r.getOrderLitres(), r.getEstimatedPrice(),
                r.isEnabled(), t != null && r.isBelow(t.getCurrentLitres()), r.getLastCheckedAt(), r.getLastDraftedAt());
    }
}
