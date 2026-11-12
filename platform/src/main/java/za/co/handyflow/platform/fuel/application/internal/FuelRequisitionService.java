package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.fuel.domain.model.FuelPurchaseOrder;
import za.co.handyflow.platform.fuel.domain.model.FuelPurchaseRequisition;
import za.co.handyflow.platform.fuel.domain.model.FuelSupplier;
import za.co.handyflow.platform.fuel.domain.repository.FuelPurchaseOrderRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPurchaseRequisitionRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelSupplierRepository;
import za.co.handyflow.platform.fuel.dto.PurchaseOrderRequest;
import za.co.handyflow.platform.fuel.dto.PurchaseOrderResponse;
import za.co.handyflow.platform.fuel.dto.RequisitionDtos.ConvertRequest;
import za.co.handyflow.platform.fuel.dto.RequisitionDtos.RequisitionRequest;
import za.co.handyflow.platform.fuel.dto.RequisitionDtos.RequisitionView;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Purchase requisitions (B05): a request to buy fuel that is approved before anyone orders it. Approval uses the Approvals engine
 * like purchase orders do (no rule means approved straight away), and an approved requisition becomes a DRAFT purchase order,
 * which still goes through the purchase order's own approval when it is placed. No thresholds are built in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelRequisitionService {

    private final FuelPurchaseRequisitionRepository requisitions;
    private final FuelSupplierRepository suppliers;
    private final FuelPurchaseOrderRepository orders;
    private final FuelPurchasingService purchasing;
    private final RequisitionNumberGenerator numbers;
    private final ApprovalFacade approvalFacade;

    @Transactional(readOnly = true)
    public List<RequisitionView> list(TenantId tenantId) {
        Map<UUID, String> supplierNames = new HashMap<>();
        suppliers.findAllActive(tenantId).forEach(s -> supplierNames.put(s.getId(), s.getName()));
        Map<UUID, String> poNumbers = new HashMap<>();
        orders.findAllActive(tenantId).forEach(p -> poNumbers.put(p.getId(), p.getPoNumber()));
        return requisitions.findAllForTenant(tenantId).stream().map(r -> view(r, supplierNames, poNumbers)).toList();
    }

    @Transactional
    public RequisitionView create(TenantId tenantId, RequisitionRequest req, UUID by) {
        if (req.supplierId() != null) requireSupplier(tenantId, req.supplierId());
        FuelPurchaseRequisition r = FuelPurchaseRequisition.create(tenantId, numbers.generate(tenantId), req.fuelType(), req.litres(), req.tankId(), req.supplierId(),
                req.estimatedPrice(), req.neededBy(), req.reason(), by);
        return one(tenantId, requisitions.save(r));
    }

    /** Drafted by an automation rule: the same as create, with no person behind it and a link back to the rule. Never submitted here. */
    @Transactional
    public RequisitionView createFromRule(TenantId tenantId, RequisitionRequest req, UUID ruleId) {
        if (req.supplierId() != null) requireSupplier(tenantId, req.supplierId());
        FuelPurchaseRequisition r = FuelPurchaseRequisition.create(tenantId, numbers.generate(tenantId), req.fuelType(), req.litres(), req.tankId(), req.supplierId(),
                req.estimatedPrice(), req.neededBy(), req.reason(), null);
        r.madeByRule(ruleId);
        return one(tenantId, requisitions.save(r));
    }

    @Transactional
    public RequisitionView update(TenantId tenantId, UUID id, RequisitionRequest req) {
        FuelPurchaseRequisition r = lock(tenantId, id);
        if (req.supplierId() != null) requireSupplier(tenantId, req.supplierId());
        r.edit(req.fuelType(), req.litres(), req.tankId(), req.supplierId(), req.estimatedPrice(), req.neededBy(), req.reason());
        return one(tenantId, requisitions.save(r));
    }

    /** The Approvals engine decides. A failure here is not swallowed: it is better to ask the user to try again than to treat an unapproved request as approved. */
    @Transactional
    public RequisitionView submit(TenantId tenantId, UUID id, UUID by) {
        FuelPurchaseRequisition r = lock(tenantId, id);
        if (!"DRAFT".equals(r.getStatus())) throw new IllegalStateException(r.getReqNumber() + " is " + r.getStatus().toLowerCase().replace('_', ' ') + ", so it cannot be submitted.");
        BigDecimal value = r.estimatedValue();
        ApprovalRequestResponse resp = approvalFacade.submit(tenantId, "fuel", "REQUISITION", r.getId(), by,
                Map.of("totalAmount", value.doubleValue(), "litres", r.getLitres().doubleValue(), "fuelType", r.getFuelType()));
        if ("APPROVED".equals(resp.status())) r.approveDirectly(); else r.submitForApproval(resp.id());
        return one(tenantId, requisitions.save(r));
    }

    /** Applies the Approvals engine's decision. Does nothing unless the requisition is still waiting, so repeats and races are harmless. */
    @Transactional
    public void applyApprovalOutcome(TenantId tenantId, UUID id, String outcome) {
        FuelPurchaseRequisition r = requisitions.findByIdForUpdate(tenantId, id).orElse(null);
        if (r == null) return;
        boolean changed = "APPROVED".equals(outcome) ? r.approvalGranted() : "REJECTED".equals(outcome) && r.approvalRejected("Approval was rejected");
        if (changed) requisitions.save(r);
    }

    /** Raises a DRAFT purchase order from an approved requisition, once. The requisition is locked first so two clicks cannot make two orders. */
    @Transactional
    public RequisitionView convert(TenantId tenantId, UUID id, ConvertRequest req, UUID by) {
        FuelPurchaseRequisition r = lock(tenantId, id);
        if (!"APPROVED".equals(r.getStatus())) throw new IllegalStateException(r.getReqNumber() + " is " + r.getStatus().toLowerCase().replace('_', ' ') + ". Only an approved requisition can become a purchase order.");
        UUID supplier = req != null && req.supplierId() != null ? req.supplierId() : r.getSupplierId();
        if (supplier == null) throw new IllegalArgumentException("Choose the supplier to order from.");
        BigDecimal price = req != null && req.pricePerLitre() != null ? req.pricePerLitre() : r.getEstimatedPrice();
        if (price == null) throw new IllegalArgumentException("Enter the price per litre.");
        var expected = req != null && req.expectedDate() != null ? req.expectedDate() : r.getNeededBy();
        PurchaseOrderResponse po = purchasing.create(tenantId, new PurchaseOrderRequest(supplier, r.getFuelType(), r.getLitres(), price, expected,
                "From requisition " + r.getReqNumber() + ": " + r.getReason()), by);
        r.convertedTo(po.id());
        log.info("Fuel requisition converted tenant={} requisition={} purchaseOrder={} by={}", tenantId, id, po.id(), by);
        return one(tenantId, requisitions.save(r));
    }

    @Transactional
    public RequisitionView cancel(TenantId tenantId, UUID id, String reason) {
        FuelPurchaseRequisition r = lock(tenantId, id);
        r.cancel(reason);
        return one(tenantId, requisitions.save(r));
    }

    private FuelPurchaseRequisition lock(TenantId tenantId, UUID id) {
        return requisitions.findByIdForUpdate(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Requisition", id.toString()));
    }

    private void requireSupplier(TenantId tenantId, UUID supplierId) {
        FuelSupplier s = suppliers.findActiveById(tenantId, supplierId).orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId.toString()));
        if (!s.isApproved()) throw new IllegalStateException(s.getName() + " is not an approved supplier.");
    }

    private RequisitionView one(TenantId tenantId, FuelPurchaseRequisition r) {
        Map<UUID, String> sn = new HashMap<>(), pn = new HashMap<>();
        if (r.getSupplierId() != null) suppliers.findActiveById(tenantId, r.getSupplierId()).ifPresent(s -> sn.put(s.getId(), s.getName()));
        if (r.getPurchaseOrderId() != null) orders.findById(r.getPurchaseOrderId()).filter(p -> p.getTenantId().equals(tenantId)).ifPresent(p -> pn.put(p.getId(), p.getPoNumber()));
        return view(r, sn, pn);
    }

    static RequisitionView view(FuelPurchaseRequisition r, Map<UUID, String> supplierNames, Map<UUID, String> poNumbers) {
        BigDecimal value = r.getEstimatedPrice() == null ? null : r.getLitres().multiply(r.getEstimatedPrice()).setScale(2, java.math.RoundingMode.HALF_UP);
        return new RequisitionView(r.getId(), r.getReqNumber(), r.getStatus(), r.getFuelType(), r.getLitres(), r.getTankId(), r.getSupplierId(),
                r.getSupplierId() == null ? null : supplierNames.get(r.getSupplierId()), r.getEstimatedPrice(), value, r.getNeededBy(), r.getReason(), r.getRequestedBy(),
                r.getApprovalNote(), r.getPurchaseOrderId(), r.getPurchaseOrderId() == null ? null : poNumbers.get(r.getPurchaseOrderId()), r.getCancelReason(), r.getCreatedAt());
    }
}
