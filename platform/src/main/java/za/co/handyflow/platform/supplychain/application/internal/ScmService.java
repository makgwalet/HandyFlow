package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.supplychain.domain.model.*;
import za.co.handyflow.platform.supplychain.domain.repository.*;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.dto.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmService {

    private final ScSupplierRepository        supplierRepo;
    private final ScStockLocationRepository   locationRepo;
    private final ScInventoryRepository       inventoryRepo;
    private final ScStockMovementRepository   movementRepo;
    private final ScPurchaseOrderRepository   poRepo;
    private final ScGoodsReceiptRepository    grRepo;
    private final ScSupplierInvoiceRepository invoiceRepo;
    private final ScPoLineRepository            poLineRepo;

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ScmSummaryResponse getSummary(TenantId tenantId) {
        long totalSuppliers   = supplierRepo.countByTenantIdAndStatus(tenantId.getValue(), "ACTIVE");
        long openPOs          = poRepo.countByTenantIdAndStatusIn(tenantId.getValue(),
                List.of("APPROVED","SENT","ACKNOWLEDGED","PARTIALLY_RECEIVED"));
        long pendingInvoices  = invoiceRepo.countByTenantIdAndStatus(tenantId.getValue(), "RECEIVED");
        long approvalInvoices = invoiceRepo.countByTenantIdAndStatus(tenantId.getValue(), "APPROVED");
        long lowStockItems    = inventoryRepo.findLowStock(tenantId.getValue()).size();
        long overdueInvoices  = invoiceRepo.findOverdue(tenantId.getValue()).size();
        return new ScmSummaryResponse(totalSuppliers, openPOs, pendingInvoices,
                approvalInvoices, lowStockItems, overdueInvoices);
    }

    // ── Suppliers ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ScSupplier> getSuppliers(TenantId tenantId, String search, String status, Pageable pageable) {
        if (search != null && !search.isBlank()) return supplierRepo.search(tenantId.getValue(), search, pageable);
        if (status != null && !status.isBlank()) return supplierRepo.findByTenantIdAndStatus(tenantId.getValue(), status, pageable);
        return supplierRepo.findByTenantId(tenantId.getValue(), pageable);
    }

    @Transactional(readOnly = true)
    public ScSupplier getSupplier(TenantId tenantId, UUID id) {
        return supplierRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Supplier not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    @Transactional
    public ScSupplier createSupplier(TenantId tenantId, UUID userId, CreateSupplierRequest req) {
        ScSupplier s = ScSupplier.create(
                tenantId.getValue(), req.name(), req.registrationNumber(), req.vatNumber(),
                req.bbbeeLevel(), req.bbbeeExpiry(), req.contactName(),
                req.contactEmail(), req.contactPhone(), req.website(),
                req.street(), req.suburb(), req.city(), req.province(), req.postalCode(),
                req.bankName(), req.bankAccount(), req.bankBranchCode(),
                req.paymentTermsDays() != null ? req.paymentTermsDays() : 30,
                req.currency(), req.notes(), userId);
        return supplierRepo.save(s);
    }

    @Transactional
    public ScSupplier updateSupplier(TenantId tenantId, UUID id, UpdateSupplierRequest req) {
        ScSupplier s = getSupplier(tenantId, id);
        s.update(req.name(), req.contactName(), req.contactEmail(), req.contactPhone(),
                req.bbbeeLevel(), req.bbbeeExpiry(), req.paymentTermsDays(),
                req.status(), req.notes());
        return supplierRepo.save(s);
    }

    // ── Locations ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScStockLocation> getLocations(TenantId tenantId) {
        return locationRepo.findActiveByTenantId(tenantId.getValue());
    }

    @Transactional
    public ScStockLocation createLocation(TenantId tenantId, CreateLocationRequest req) {
        boolean isFirst = locationRepo.findActiveByTenantId(tenantId.getValue()).isEmpty();
        ScStockLocation loc = ScStockLocation.create(tenantId.getValue(), req.name(),
                req.locationType(), req.address(),
                isFirst || Boolean.TRUE.equals(req.isDefault()));
        return locationRepo.save(loc);
    }

    // ── Inventory ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScInventory> getInventory(TenantId tenantId, UUID locationId) {
        if (locationId != null) return inventoryRepo.findByTenantIdAndLocation(tenantId.getValue(), locationId);
        return inventoryRepo.findByTenantId(tenantId.getValue());
    }

    @Transactional(readOnly = true)
    public List<ScInventory> getLowStockAlerts(TenantId tenantId) {
        return inventoryRepo.findLowStock(tenantId.getValue());
    }

    @Transactional
    public ScInventory openingStock(TenantId tenantId, UUID userId, String userName, OpeningStockRequest req) {
        locationRepo.findActiveByTenantIdAndId(tenantId.getValue(), req.locationId())
                .orElseThrow(() -> new HandyFlowException("Location not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        ScInventory inv = inventoryRepo
                .findByTenantIdAndLocationIdAndCatalogueItemId(tenantId.getValue(), req.locationId(), req.catalogueItemId())
                .orElseGet(() -> ScInventory.create(tenantId.getValue(), req.locationId(), req.catalogueItemId()));

        BigDecimal qty  = req.qty();
        BigDecimal cost = req.unitCost() != null ? req.unitCost() : BigDecimal.ZERO;
        BigDecimal before = inv.getQtyOnHand();

        inv.setReorderLevels(req.reorderPoint(), req.reorderQty());
        if (req.binLocation() != null) inv.setBinLocation(req.binLocation());
        inv = inventoryRepo.save(inv);

        BigDecimal delta = qty.subtract(before);
        ScStockMovement mv = ScStockMovement.record(tenantId.getValue(), inv, "OPENING",
                delta, cost, "OPENING", null, null, userId, userName);
        inv.adjustQty(delta, cost);
        inventoryRepo.save(inv);
        movementRepo.save(mv);
        return inv;
    }

    @Transactional(readOnly = true)
    public List<ScStockMovement> getMovements(TenantId tenantId, UUID inventoryId, Pageable pageable) {
        return movementRepo.findByInventoryId(inventoryId, pageable).getContent();
    }

    // ── Purchase Orders ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ScPurchaseOrder> getPurchaseOrders(TenantId tenantId, String status, Pageable pageable) {
        if (status != null && !status.isBlank()) return poRepo.findByTenantIdAndStatus(tenantId.getValue(), status, pageable);
        return poRepo.findByTenantId(tenantId.getValue(), pageable);
    }

    @Transactional(readOnly = true)
    public ScPurchaseOrder getPurchaseOrder(TenantId tenantId, UUID id) {
        return poRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Purchase order not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    @Transactional
    public ScPurchaseOrder createPurchaseOrder(TenantId tenantId, UUID userId, String userName,
                                                CreatePurchaseOrderRequest req) {
        ScSupplier supplier = getSupplier(tenantId, req.supplierId());
        int seq = poRepo.findMaxOrderSequence(tenantId.getValue()) + 1;
        String orderNumber = "PO-" + String.format("%05d", seq);
        ScPurchaseOrder po = ScPurchaseOrder.create(tenantId.getValue(), orderNumber,
                supplier.getId(), supplier.getName(),
                req.deliverToLocation(),
                req.orderDate() != null ? req.orderDate() : LocalDate.now(),
                req.requiredByDate(), req.currency(), req.projectRef(),
                req.notes(), req.internalNotes(), userId, userName);
        return poRepo.save(po);
    }

    @Transactional
    public ScPurchaseOrder submitForApproval(TenantId tenantId, UUID id) {
        ScPurchaseOrder po = getPurchaseOrder(tenantId, id);
        po.submit();
        return poRepo.save(po);
    }

    @Transactional
    public ScPurchaseOrder approvePurchaseOrder(TenantId tenantId, UUID id, UUID approverId, String approverName) {
        ScPurchaseOrder po = getPurchaseOrder(tenantId, id);
        po.approve(approverId, approverName);
        log.info("PO {} approved by {}", po.getOrderNumber(), approverName);
        return poRepo.save(po);
    }

    @Transactional
    public ScPurchaseOrder rejectPurchaseOrder(TenantId tenantId, UUID id, String reason) {
        ScPurchaseOrder po = getPurchaseOrder(tenantId, id);
        po.reject(reason);
        return poRepo.save(po);
    }

    @Transactional
    public ScPurchaseOrder markSent(TenantId tenantId, UUID id) {
        ScPurchaseOrder po = getPurchaseOrder(tenantId, id);
        po.send();
        return poRepo.save(po);
    }

    /**
     * Add a line item to a DRAFT purchase order.
     * Uses ScPoLine.create() directly — ScPurchaseOrder has no lines collection.
     * After saving the line, recalculate the PO totals by summing all lines.
     */
    @Transactional
    public ScPurchaseOrder addPurchaseOrderLine(TenantId tenantId, UUID poId, AddPoLineRequest req) {
        ScPurchaseOrder po = getPurchaseOrder(tenantId, poId);

        if (!"DRAFT".equals(po.getStatus())) {
            throw new HandyFlowException(
                    "Lines can only be added to DRAFT purchase orders",
                    HttpStatus.BAD_REQUEST, "INVALID_STATUS");
        }

        // ScPoLine.create(tenantId, purchaseOrderId, catalogueItemId, itemName,
        //                 supplierSku, qtyOrdered, unitCost, vatRate)
        ScPoLine line = ScPoLine.create(
                tenantId.getValue(),
                po.getId(),
                req.catalogueItemId(),       // nullable — free-text items have no catalogue link
                req.itemName(),
                req.supplierSku(),           // nullable
                req.qtyOrdered(),
                req.unitCost(),
                req.vatRate()                // nullable — ScPoLine defaults to 15%
        );
        poLineRepo.save(line);

        // Recalculate PO totals from all lines
        recalculatePoTotals(po);

        return poRepo.save(po);
    }

    /**
     * Returns PO with its lines embedded for the detail view.
     * The lines are fetched from sc_po_lines and attached to a response DTO —
     * ScPurchaseOrder is a flat entity with no @OneToMany lines collection.
     */
    @Transactional(readOnly = true)
    public ScPurchaseOrder getPurchaseOrderWithLines(TenantId tenantId, UUID id) {
        // Returns the PO entity — lines are fetched separately in the controller/DTO layer
        // and merged by ScmController's GET /purchase-orders/{id} endpoint.
        return getPurchaseOrder(tenantId, id);
    }

    public List<ScPoLine> getPurchaseOrderLines(TenantId tenantId, UUID poId) {
        // Verify PO belongs to tenant before returning lines
        getPurchaseOrder(tenantId, poId); // throws 404 if not found
        return poLineRepo.findByPurchaseOrderId(poId);
    }

    // ── Private helper ─────────────────────────────────────────────────────────

    private void recalculatePoTotals(ScPurchaseOrder po) {
        List<ScPoLine> lines = poLineRepo.findByPurchaseOrderId(po.getId());
        // ScPurchaseOrder fields: subtotal, vatAmount, totalAmount
        // Access via reflection or direct field setting — entity has no setters.
        // Since these are non-final package-accessible fields, use a dedicated
        // recalculate() approach. The cleanest fix: add setters or a recalculate()
        // method to ScPurchaseOrder. For now we use the existing fields directly.
        // NOTE: add this method to ScPurchaseOrder entity:
        //   public void recalculateTotals(BigDecimal subtotal, BigDecimal vatAmount, BigDecimal totalAmount) {
        //       this.subtotal = subtotal; this.vatAmount = vatAmount; this.totalAmount = totalAmount; touch();
        //   }
        java.math.BigDecimal sub = lines.stream()
                .map(l -> l.getLineTotal())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal vat = lines.stream()
                .map(l -> l.getVatAmount())
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        po.recalculateTotals(sub, vat, sub.add(vat));
    }


    // ── Goods Receipts ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ScGoodsReceipt> getGoodsReceipts(TenantId tenantId, Pageable pageable) {
        return grRepo.findByTenantId(tenantId.getValue(), pageable);
    }

    @Transactional
    public ScGoodsReceipt createGoodsReceipt(TenantId tenantId, UUID userId, String userName,
                                               CreateGoodsReceiptRequest req) {
        ScPurchaseOrder po = getPurchaseOrder(tenantId, req.purchaseOrderId());
        if (!po.canReceive())
            throw new HandyFlowException("PO is not in a receivable state", HttpStatus.BAD_REQUEST, "INVALID_STATE");
        int seq = grRepo.findMaxReceiptSequence(tenantId.getValue()) + 1;
        String receiptNumber = "GR-" + String.format("%05d", seq);
        ScGoodsReceipt gr = ScGoodsReceipt.create(tenantId.getValue(), receiptNumber, po.getId(),
                po.getSupplierId(), req.receivedToLocation(), req.deliveryNoteRef(),
                userId, userName,
                req.receivedDate() != null ? req.receivedDate() : LocalDate.now(),
                req.notes());
        return grRepo.save(gr);
    }

    @Transactional
    public ScGoodsReceipt postGoodsReceipt(TenantId tenantId, UUID grId, UUID userId, String userName,
                                            List<PostGrLineRequest> lines) {
        ScGoodsReceipt gr = grRepo.findByTenantIdAndId(tenantId.getValue(), grId)
                .orElseThrow(() -> new HandyFlowException("Goods receipt not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        UUID locationId = gr.getReceivedTo();
        for (PostGrLineRequest line : lines) {
            if (line.qtyReceived() == null || line.qtyReceived().compareTo(BigDecimal.ZERO) <= 0) continue;
            UUID catItemId = line.catalogueItemId();
            ScInventory inv = inventoryRepo
                    .findByTenantIdAndLocationIdAndCatalogueItemId(tenantId.getValue(), locationId, catItemId)
                    .orElseGet(() -> inventoryRepo.save(ScInventory.create(tenantId.getValue(), locationId, catItemId)));

            BigDecimal unitCost = line.unitCost() != null ? line.unitCost() : BigDecimal.ZERO;
            ScStockMovement mv = ScStockMovement.record(tenantId.getValue(), inv, "PURCHASE",
                    line.qtyReceived(), unitCost,
                    "GOODS_RECEIPT", gr.getId(), gr.getReceiptNumber(), userId, userName);
            inv.adjustQty(line.qtyReceived(), unitCost);
            inventoryRepo.save(inv);
            movementRepo.save(mv);
        }

        gr.post();
        gr = grRepo.save(gr);

        ScPurchaseOrder po = getPurchaseOrder(tenantId, gr.getPurchaseOrderId());
        po.partiallyReceive();
        poRepo.save(po);

        log.info("GR {} posted — stock updated", gr.getReceiptNumber());
        return gr;
    }

    // ── Supplier Invoices ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ScSupplierInvoice> getSupplierInvoices(TenantId tenantId, String status, Pageable pageable) {
        if (status != null && !status.isBlank())
            return invoiceRepo.findByTenantIdAndStatus(tenantId.getValue(), status, pageable);
        return invoiceRepo.findByTenantId(tenantId.getValue(), pageable);
    }

    @Transactional
    public ScSupplierInvoice createSupplierInvoice(TenantId tenantId, CreateSupplierInvoiceRequest req) {
        int seq = invoiceRepo.findMaxInvoiceSequence(tenantId.getValue()) + 1;
        String invoiceNumber = "SINV-" + String.format("%05d", seq);
        ScSupplierInvoice inv = ScSupplierInvoice.create(tenantId.getValue(), invoiceNumber,
                req.supplierId(), req.purchaseOrderId(), req.goodsReceiptId(),
                req.supplierInvoiceRef(), req.invoiceDate(), req.dueDate(),
                req.currency(), req.subtotal(), req.vatAmount(), req.totalAmount(),
                req.notes());
        return invoiceRepo.save(inv);
    }

    @Transactional
    public ScSupplierInvoice approveSupplierInvoice(TenantId tenantId, UUID id, UUID approverId, String approverName) {
        ScSupplierInvoice inv = invoiceRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Invoice not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        inv.approve(approverId, approverName);
        log.info("Supplier invoice {} approved by {}", inv.getInvoiceNumber(), approverName);
        return invoiceRepo.save(inv);
    }

    @Transactional
    public ScSupplierInvoice markPaid(TenantId tenantId, UUID id, String paymentRef) {
        ScSupplierInvoice inv = invoiceRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Invoice not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        inv.markPaid(paymentRef);
        return invoiceRepo.save(inv);
    }
}
