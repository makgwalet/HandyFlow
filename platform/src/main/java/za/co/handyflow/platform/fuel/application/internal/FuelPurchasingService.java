package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelPurchaseOrder;
import za.co.handyflow.platform.fuel.domain.model.FuelReceipt;
import za.co.handyflow.platform.fuel.domain.repository.FuelPurchaseOrderRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelReceiptRepository;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.fuel.domain.model.FuelSupplier;
import za.co.handyflow.platform.fuel.domain.repository.FuelSupplierRepository;
import za.co.handyflow.platform.fuel.dto.PurchaseOrderMatchResponse;
import za.co.handyflow.platform.fuel.dto.PurchaseOrderRequest;
import za.co.handyflow.platform.fuel.dto.PurchaseOrderResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stage 4C: orders placed with fuel suppliers, and receipts matched against them.
 * Matching is on litres and unit price. It does not match supplier invoices: that is an Accounts Payable job.
 */
@Service
@RequiredArgsConstructor
public class FuelPurchasingService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelPurchaseOrderRepository orders;
    private final FuelReceiptRepository receipts;
    private final FuelSupplierRepository suppliers;
    private final PurchaseOrderNumberGenerator numbers;
    private final FuelSettingsService settings;
    private final ApprovalFacade approvalFacade;

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> list(TenantId tenantId) {
        Map<UUID, BigDecimal> received = new HashMap<>();
        for (Object[] row : receipts.sumReceivedByPo(tenantId)) received.put((UUID) row[0], (BigDecimal) row[1]);
        Map<UUID, String> names = new HashMap<>();
        return orders.findAllActive(tenantId).stream()
                .map(p -> toResponse(tenantId, p, received.getOrDefault(p.getId(), BigDecimal.ZERO), names)).toList();
    }

    @Transactional
    public PurchaseOrderResponse create(TenantId tenantId, PurchaseOrderRequest req, UUID by) {
        requireSupplier(tenantId, req.supplierId());
        String number = numbers.generate(tenantId);
        FuelPurchaseOrder p = FuelPurchaseOrder.create(tenantId, number, req.supplierId(), req.fuelType(), req.litresOrdered(),
                req.pricePerLitre(), req.expectedDate(), req.notes(), by);
        return toResponse(tenantId, orders.save(p), BigDecimal.ZERO, new HashMap<>());
    }

    @Transactional
    public PurchaseOrderResponse update(TenantId tenantId, UUID id, PurchaseOrderRequest req) {
        FuelPurchaseOrder p = find(tenantId, id);
        if (!p.getSupplierId().equals(req.supplierId())) throw new IllegalArgumentException("The supplier cannot be changed. Cancel this order and raise a new one.");
        p.edit(req.fuelType(), req.litresOrdered(), req.pricePerLitre(), req.expectedDate(), req.notes());
        return toResponse(tenantId, orders.save(p), BigDecimal.ZERO, new HashMap<>());
    }

    @Transactional
    public PurchaseOrderResponse place(TenantId tenantId, UUID id, UUID by) {
        FuelPurchaseOrder p = find(tenantId, id);
        requireSupplier(tenantId, p.getSupplierId());
        if (!"DRAFT".equals(p.getStatus())) throw new IllegalStateException(p.getPoNumber() + " is " + p.getStatus() + ", not a draft.");
        // The Approvals engine decides: no rule for fuel purchase orders means approved straight away. A failure here is not swallowed,
        // because placing an order that should have waited for approval is worse than asking the user to try again.
        BigDecimal value = p.getLitresOrdered().multiply(p.getPricePerLitre()).setScale(2, java.math.RoundingMode.HALF_UP);
        ApprovalRequestResponse r = approvalFacade.submit(tenantId, "fuel", "PURCHASE_ORDER", p.getId(), by,
                Map.of("totalAmount", value.doubleValue(), "litres", p.getLitresOrdered().doubleValue(), "fuelType", p.getFuelType()));
        if ("APPROVED".equals(r.status())) p.place(); else p.submitForApproval(r.id());
        return toResponse(tenantId, orders.save(p), BigDecimal.ZERO, new HashMap<>());
    }

    /** Applies an approval outcome from the Approvals engine. Does nothing unless the order is still waiting, so repeats and races are harmless. */
    @Transactional
    public void applyApprovalOutcome(TenantId tenantId, UUID poId, String outcome) {
        FuelPurchaseOrder p = orders.findActiveByIdForUpdate(tenantId, poId).orElse(null);
        if (p == null) return;
        boolean changed = "APPROVED".equals(outcome) ? p.approvalGranted() : "REJECTED".equals(outcome) && p.approvalRejected("Approval was rejected");
        if (changed) orders.save(p);
    }

    @Transactional
    public PurchaseOrderResponse cancel(TenantId tenantId, UUID id, String reason) {
        FuelPurchaseOrder p = find(tenantId, id);
        BigDecimal received = receipts.sumReceivedForPo(tenantId, id);
        if (received.signum() > 0) throw new IllegalStateException(p.getPoNumber() + " has " + received + " L received against it, so it cannot be cancelled.");
        p.cancel(reason);
        return toResponse(tenantId, orders.save(p), BigDecimal.ZERO, new HashMap<>());
    }

    @Transactional
    public PurchaseOrderResponse closeShort(TenantId tenantId, UUID id, String reason) {
        FuelPurchaseOrder p = orders.findActiveByIdForUpdate(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Purchase order", id.toString()));
        // Re-sync first so a status that lagged behind the receipts is not closed on stale numbers.
        p.applyReceived(receipts.sumReceivedForPo(tenantId, id), settings.poToleranceFraction(tenantId));
        p.closeShort(reason);
        orders.save(p);
        return toResponse(tenantId, p, receipts.sumReceivedForPo(tenantId, id), new HashMap<>());
    }

    @Transactional
    public void remove(TenantId tenantId, UUID id) {
        FuelPurchaseOrder p = find(tenantId, id);
        p.softDelete();
        orders.save(p);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderMatchResponse match(TenantId tenantId, UUID id) {
        FuelPurchaseOrder p = find(tenantId, id);
        List<FuelReceipt> rs = receipts.findByPo(tenantId, id);
        BigDecimal litres = BigDecimal.ZERO, value = BigDecimal.ZERO;
        List<PurchaseOrderMatchResponse.Line> lines = new java.util.ArrayList<>();
        for (FuelReceipt r : rs) {
            BigDecimal diff = r.getPricePerLitre().subtract(p.getPricePerLitre());
            if (!r.isReversed()) { litres = litres.add(r.getLitresReceived()); value = value.add(r.getTotalCost()); }
            lines.add(new PurchaseOrderMatchResponse.Line(r.getId(), r.getReceivedAt(), r.getTankId(), r.getLitresReceived(), r.getPricePerLitre(),
                    diff, r.getTotalCost(), r.getDeliveryNote(), r.getInvoiceRef(), r.isReversed(), diff.abs().compareTo(new BigDecimal("0.0001")) > 0));
        }
        BigDecimal ordered = p.getLitresOrdered().multiply(p.getPricePerLitre()).setScale(2, java.math.RoundingMode.HALF_UP);
        return new PurchaseOrderMatchResponse(toResponse(tenantId, p, litres, new HashMap<>()), value, value.subtract(ordered),
                litres.subtract(p.getLitresOrdered()), lines);
    }

    /**
     * Called by receiving, before stock moves. Locks the order and refuses a delivery that does not belong to it.
     * @return the locked order
     */
    @Transactional
    public FuelPurchaseOrder checkReceivable(TenantId tenantId, UUID poId, UUID supplierId, String tankFuelType, BigDecimal litres) {
        FuelPurchaseOrder p = orders.findActiveByIdForUpdate(tenantId, poId).orElseThrow(() -> new ResourceNotFoundException("Purchase order", poId.toString()));
        if (!p.canReceive()) {
            throw new IllegalStateException(p.getPoNumber() + " is " + p.getStatus() + (("DRAFT".equals(p.getStatus())) ? ": place it with the supplier first." : ", so nothing more can be received against it."));
        }
        if (supplierId == null || !supplierId.equals(p.getSupplierId())) throw new IllegalArgumentException("This delivery's supplier is not the supplier on " + p.getPoNumber() + ".");
        if (tankFuelType != null && !tankFuelType.equalsIgnoreCase(p.getFuelType())) {
            throw new IllegalArgumentException(p.getPoNumber() + " is for " + p.getFuelType() + " but this tank holds " + tankFuelType + ".");
        }
        BigDecimal so = receipts.sumReceivedForPo(tenantId, poId);
        BigDecimal tolerance = settings.poToleranceFraction(tenantId);
        if (so.add(litres).compareTo(p.maxReceivable(tolerance)) > 0) {
            throw new IllegalStateException("That would be " + so.add(litres) + " L received against " + p.getPoNumber() + ", which ordered "
                    + p.getLitresOrdered() + " L (up to " + p.maxReceivable(tolerance) + " L is accepted).");
        }
        return p;
    }

    /** Called after a receipt is saved or reversed: the order's status follows the litres actually received. */
    @Transactional
    public void resync(TenantId tenantId, UUID poId) {
        if (poId == null) return;
        orders.findActiveByIdForUpdate(tenantId, poId).ifPresent(p -> {
            p.applyReceived(receipts.sumReceivedForPo(tenantId, poId), settings.poToleranceFraction(tenantId));
            orders.save(p);
        });
    }

    private void requireSupplier(TenantId tenantId, UUID supplierId) {
        FuelSupplier s = suppliers.findActiveById(tenantId, supplierId).orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId.toString()));
        if (!s.isApproved()) {
            throw new IllegalStateException(s.getName() + " is " + ("SUSPENDED".equals(s.getApprovalStatus()) ? "suspended" : "waiting for approval") + ", so orders cannot be raised or placed with them.");
        }
    }

    private FuelPurchaseOrder find(TenantId tenantId, UUID id) {
        return orders.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Purchase order", id.toString()));
    }

    private PurchaseOrderResponse toResponse(TenantId tenantId, FuelPurchaseOrder p, BigDecimal received, Map<UUID, String> names) {
        String name = names.computeIfAbsent(p.getSupplierId(), sid -> suppliers.findActiveById(tenantId, sid).map(s -> s.getName()).orElse("Unknown supplier"));
        BigDecimal outstanding = p.getLitresOrdered().subtract(received).max(BigDecimal.ZERO);
        boolean overdue = p.canReceive() && p.getExpectedDate() != null && p.getExpectedDate().isBefore(LocalDate.now(SAST));
        return new PurchaseOrderResponse(p.getId(), p.getPoNumber(), p.getSupplierId(), name, p.getFuelType(), p.getLitresOrdered(), received,
                outstanding, p.getPricePerLitre(), p.getLitresOrdered().multiply(p.getPricePerLitre()).setScale(2, java.math.RoundingMode.HALF_UP),
                p.getStatus(), p.getExpectedDate(), overdue, p.getCancelReason(), p.getCloseReason(), p.getNotes(), p.getApprovalNote());
    }
}
