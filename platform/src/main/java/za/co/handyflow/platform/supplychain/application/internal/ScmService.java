package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.enums.*;
import za.co.handyflow.platform.supplychain.domain.model.*;
import za.co.handyflow.platform.supplychain.domain.repository.*;
import za.co.handyflow.platform.supplychain.dto.*;
import za.co.handyflow.platform.projects.application.internal.SequenceService;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final ScGrLineRepository          grLineRepo;
    private final ScSupplierInvoiceRepository invoiceRepo;
    private final ScPoLineRepository          poLineRepo;
    private final ScSupplierItemRepository    supplierItemRepo;
    private final SequenceService             sequenceService;
    private final ScmNotificationService      notificationService;

    /** Tolerance for 3-way match: invoice may differ from PO by up to this fraction. */
    private static final BigDecimal MATCH_TOLERANCE = new BigDecimal("0.02"); // 2%

    // ── Summary ───────────────────────────────────────────────────────────────

    /**
     * WHY COUNT queries instead of list.size()?
     * Previously: inventoryRepo.findLowStock().size() loaded all entity columns
     * of every low-stock row just to count them. SELECT COUNT(*) returns one integer.
     * For 500 SKUs with 50 low-stock items that's ~50× less data over the network.
     */
    @Transactional(readOnly = true)
    public ScmSummaryResponse getSummary(TenantId tenantId) {
        UUID tid = tenantId.getValue();
        long totalSuppliers   = supplierRepo.countByTenantIdAndStatus(tid, SupplierStatus.ACTIVE);
        long openPOs          = poRepo.countByTenantIdAndStatusIn(tid,
                List.of(PoStatus.APPROVED, PoStatus.SENT, PoStatus.ACKNOWLEDGED, PoStatus.PARTIALLY_RECEIVED));
        long pendingInvoices  = invoiceRepo.countByTenantIdAndStatus(tid, InvoiceStatus.RECEIVED);
        long approvalInvoices = invoiceRepo.countByTenantIdAndStatus(tid, InvoiceStatus.APPROVED);
        long lowStockItems    = inventoryRepo.countLowStock(tid);    // FIX C-2: was .size()
        long overdueInvoices  = invoiceRepo.countOverdue(tid);       // FIX C-2: was .size()
        return new ScmSummaryResponse(totalSuppliers, openPOs, pendingInvoices,
                approvalInvoices, lowStockItems, overdueInvoices);
    }

    // ── Suppliers ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ScSupplier> getSuppliers(TenantId tenantId, String search, String status, Pageable pageable) {
        UUID tid = tenantId.getValue();
        if (search != null && !search.isBlank())
            return supplierRepo.search(tid, search, pageable);
        if (status != null && !status.isBlank()) {
            SupplierStatus statusEnum = SupplierStatus.valueOf(status.toUpperCase());
            return supplierRepo.findByTenantIdAndStatus(tid, statusEnum, pageable);
        }
        return supplierRepo.findByTenantId(tid, pageable);
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
        SupplierStatus statusEnum = req.status() != null
                ? SupplierStatus.valueOf(req.status().toUpperCase()) : null;
        s.update(req.name(), req.contactName(), req.contactEmail(), req.contactPhone(),
                req.bbbeeLevel(), req.bbbeeExpiry(), req.paymentTermsDays(),
                statusEnum, req.notes());
        return supplierRepo.save(s);
    }

    // ── Supplier items (pricing catalogue) ────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScSupplierItem> getSupplierItems(TenantId tenantId, UUID supplierId) {
        getSupplier(tenantId, supplierId); // verify supplier belongs to tenant
        return supplierItemRepo.findBySupplierId(supplierId);
    }

    @Transactional(readOnly = true)
    public List<ScSupplierItem> getBestPriceForItem(TenantId tenantId, UUID catalogueItemId) {
        return supplierItemRepo.findByCatalogueItemOrderedByPrice(tenantId.getValue(), catalogueItemId);
    }

    @Transactional
    public ScSupplierItem addSupplierItem(TenantId tenantId, UUID supplierId, AddSupplierItemRequest req) {
        getSupplier(tenantId, supplierId);
        ScSupplierItem item = ScSupplierItem.create(
                tenantId.getValue(), supplierId,
                req.catalogueItemId(), req.itemName(), req.supplierSku(),
                req.unitCost(), req.leadTimeDays() != null ? req.leadTimeDays() : 7,
                req.minOrderQty(), Boolean.TRUE.equals(req.isPreferred()));
        return supplierItemRepo.save(item);
    }

    // ── Stock locations ───────────────────────────────────────────────────────

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
        UUID tid = tenantId.getValue();
        return locationId != null
                ? inventoryRepo.findByTenantIdAndLocation(tid, locationId)
                : inventoryRepo.findByTenantId(tid);
    }

    @Transactional(readOnly = true)
    public List<ScInventory> getLowStockAlerts(TenantId tenantId) {
        return inventoryRepo.findLowStock(tenantId.getValue());
    }

    /**
     * Sets opening stock for an item at a location.
     *
     * FIX C-3 / C-4: Uses upsertInventoryRow() to safely create or skip-create
     * the inventory record. This is atomic at the DB level — two concurrent requests
     * for the same item+location will not crash.
     *
     * FIX M-7: Zero-delta guard — if requested qty equals current qty, no movement
     * is created. The reorder levels and bin location are still updated.
     */
    @Transactional
    public ScInventory openingStock(TenantId tenantId, UUID userId, String userName,
                                    OpeningStockRequest req) {
        UUID tid = tenantId.getValue();
        locationRepo.findActiveByTenantIdAndId(tid, req.locationId())
                .orElseThrow(() -> new HandyFlowException("Location not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        // Atomic upsert — safe under concurrent requests (FIX C-3)
        inventoryRepo.upsertInventoryRow(UUID.randomUUID(), tid, req.locationId(), req.catalogueItemId());
        ScInventory inv = inventoryRepo.findByTenantIdAndLocationIdAndCatalogueItemId(
                tid, req.locationId(), req.catalogueItemId()).orElseThrow();

        BigDecimal before = inv.getQtyOnHand();
        inv.setReorderLevels(req.reorderPoint(), req.reorderQty());
        if (req.binLocation() != null) inv.setBinLocation(req.binLocation());

        BigDecimal delta = req.qty().subtract(before);

        // Zero-delta guard (FIX M-7)
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            log.info("[SCM] Opening stock: no qty change for inventory={} — reorder levels updated only", inv.getId());
            return inventoryRepo.save(inv);
        }

        BigDecimal cost = req.unitCost() != null ? req.unitCost() : BigDecimal.ZERO;
        ScStockMovement mv = ScStockMovement.record(tid, inv, "OPENING",
                delta, cost, "OPENING", null, null, userId, userName);
        inv.adjustQty(delta, cost);
        inventoryRepo.save(inv);
        movementRepo.save(mv);
        return inv;
    }

    /**
     * Returns stock movement history for a specific inventory record.
     * FIX H-5: verify the inventory record belongs to this tenant before returning data.
     */
    @Transactional(readOnly = true)
    public List<ScStockMovement> getMovements(TenantId tenantId, UUID inventoryId, Pageable pageable) {
        inventoryRepo.findByTenantIdAndId(tenantId.getValue(), inventoryId)
                .orElseThrow(() -> new HandyFlowException("Inventory record not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
        return movementRepo.findByInventoryId(inventoryId, pageable).getContent();
    }

    // ── Purchase Orders ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ScPurchaseOrder> getPurchaseOrders(TenantId tenantId, String status, Pageable pageable) {
        UUID tid = tenantId.getValue();
        if (status != null && !status.isBlank()) {
            PoStatus statusEnum = PoStatus.valueOf(status.toUpperCase());
            return poRepo.findByTenantIdAndStatus(tid, statusEnum, pageable);
        }
        return poRepo.findByTenantId(tid, pageable);
    }

    @Transactional(readOnly = true)
    public ScPurchaseOrder getPurchaseOrder(TenantId tenantId, UUID id) {
        return poRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Purchase order not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    /**
     * Creates a purchase order.
     *
     * FIX C-1: PO number from SequenceService.next() — atomic PostgreSQL upsert.
     * FIX H-1: createdByName from TenantContext.getCurrentUserName() — not userId.toString().
     * FIX Guard: rejects orders against BLACKLISTED suppliers.
     *
     * WHY check supplier status here and not in the controller?
     * Business rules belong in the service (or domain) layer. The controller's
     * job is to translate HTTP ↔ service calls. If the blacklist check lived in
     * the controller, a developer adding a CLI or batch job that calls the service
     * directly would bypass it. In the service, it's enforced for every caller.
     */
    @Transactional
    public ScPurchaseOrder createPurchaseOrder(TenantId tenantId, UUID userId, String userName,
                                               CreatePurchaseOrderRequest req) {
        ScSupplier supplier = getSupplier(tenantId, req.supplierId());
        if (!supplier.isOrderable()) {
            throw new HandyFlowException(
                    "Cannot create a purchase order for a " + supplier.getStatus() + " supplier",
                    HttpStatus.BAD_REQUEST, "SUPPLIER_NOT_ORDERABLE");
        }

        // FIX C-1: atomic sequence — no race condition
        int seq = sequenceService.next(tenantId.getValue(), "PO");
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
        if (poLineRepo.countByPurchaseOrderId(id) == 0)
            throw new HandyFlowException("Cannot submit a PO with no lines",
                    HttpStatus.BAD_REQUEST, "NO_LINES");
        po.submit();
        return poRepo.save(po);
    }

    @Transactional
    public ScPurchaseOrder approvePurchaseOrder(TenantId tenantId, UUID id,
                                                UUID approverId, String approverName) {
        ScPurchaseOrder po = getPurchaseOrder(tenantId, id);
        po.approve(approverId, approverName);
        log.info("[SCM] PO {} approved by {}", po.getOrderNumber(), approverName);
        ScPurchaseOrder saved = poRepo.save(po);
        notificationService.notifyPoApproved(tenantId.getValue(), po.getOrderNumber(),
                po.getSupplierName(), po.getTotalAmount(), approverName);
        return saved;
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

    @Transactional
    public ScPurchaseOrder addPurchaseOrderLine(TenantId tenantId, UUID poId, AddPoLineRequest req) {
        ScPurchaseOrder po = getPurchaseOrder(tenantId, poId);
        if (po.getStatus() != PoStatus.DRAFT)
            throw new HandyFlowException("Lines can only be added to DRAFT purchase orders",
                    HttpStatus.BAD_REQUEST, "INVALID_STATUS");

        ScPoLine line = ScPoLine.create(tenantId.getValue(), po.getId(),
                req.catalogueItemId(), req.itemName(), req.supplierSku(),
                req.qtyOrdered(), req.unitCost(), req.vatRate());
        poLineRepo.save(line);
        recalculatePoTotals(po);
        return poRepo.save(po);
    }

    @Transactional(readOnly = true)   // FIX M-2: missing @Transactional
    public List<ScPoLine> getPurchaseOrderLines(TenantId tenantId, UUID poId) {
        getPurchaseOrder(tenantId, poId);  // tenant isolation check
        return poLineRepo.findByPurchaseOrderId(poId);
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
            throw new HandyFlowException("PO is not in a receivable state — current: " + po.getStatus(),
                    HttpStatus.BAD_REQUEST, "INVALID_STATE");

        // FIX C-1: atomic sequence for GR number
        int seq = sequenceService.next(tenantId.getValue(), "GR");
        String receiptNumber = "GR-" + String.format("%05d", seq);

        ScGoodsReceipt gr = ScGoodsReceipt.create(tenantId.getValue(), receiptNumber, po.getId(),
                po.getSupplierId(), req.receivedToLocation(), req.deliveryNoteRef(),
                userId, userName,
                req.receivedDate() != null ? req.receivedDate() : LocalDate.now(),
                req.notes());
        return grRepo.save(gr);
    }

    /**
     * Posts a goods receipt: creates stock movements, updates inventory,
     * writes GR line records, and transitions the PO status.
     *
     * FIX C-4: inventory creation uses upsertInventoryRow() — race-safe.
     * FIX H-4: ScGrLine records created for line-level audit trail.
     * FIX H-6: ScPoLine.qtyReceived updated; PO transitions to FULLY_RECEIVED
     *           when all lines are done.
     */
    @Transactional
    public ScGoodsReceipt postGoodsReceipt(TenantId tenantId, UUID grId, UUID userId, String userName,
                                           List<PostGrLineRequest> lines) {
        UUID tid = tenantId.getValue();
        ScGoodsReceipt gr = grRepo.findByTenantIdAndId(tid, grId)
                .orElseThrow(() -> new HandyFlowException("Goods receipt not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));

        if (lines == null || lines.isEmpty())
            throw new HandyFlowException("Cannot post a goods receipt with no lines",
                    HttpStatus.BAD_REQUEST, "NO_LINES");

        UUID locationId = gr.getReceivedTo();

        for (PostGrLineRequest line : lines) {
            if (line.qtyReceived() == null || line.qtyReceived().compareTo(BigDecimal.ZERO) <= 0)
                continue;

            UUID catItemId = line.catalogueItemId();

            // FIX C-4: atomic upsert — safe under concurrent GR postings
            inventoryRepo.upsertInventoryRow(UUID.randomUUID(), tid, locationId, catItemId);
            ScInventory inv = inventoryRepo
                    .findByTenantIdAndLocationIdAndCatalogueItemId(tid, locationId, catItemId)
                    .orElseThrow();

            BigDecimal unitCost = line.unitCost() != null ? line.unitCost() : BigDecimal.ZERO;

            // FIX H-4: create ScGrLine for audit trail
            // Find the PO line to link (match by catalogueItemId)
            List<ScPoLine> poLines = poLineRepo.findByPoAndCatalogueItem(gr.getPurchaseOrderId(), catItemId);
            UUID poLineId = null;
            if (!poLines.isEmpty()) {
                ScPoLine poLine = poLines.get(0);
                poLineId = poLine.getId();
                // FIX H-6: update PO line received quantity
                poLine.recordReceived(line.qtyReceived());
                poLineRepo.save(poLine);
            }

            ScGrLine grLine = ScGrLine.create(tid, grId, poLineId, catItemId,
                    line.itemName() != null ? line.itemName() : inv.getCatalogueItemId().toString(),
                    poLines.isEmpty() ? line.qtyReceived() : poLines.get(0).getQtyOrdered(),
                    line.qtyReceived(), unitCost, "GOOD");
            grLineRepo.save(grLine);

            ScStockMovement mv = ScStockMovement.record(tid, inv, "PURCHASE",
                    line.qtyReceived(), unitCost,
                    "GOODS_RECEIPT", gr.getId(), gr.getReceiptNumber(), userId, userName);
            inv.adjustQty(line.qtyReceived(), unitCost);
            inventoryRepo.save(inv);
            movementRepo.save(mv);
        }

        gr.post();
        gr = grRepo.save(gr);

        // FIX H-2 / H-6: transition PO to FULLY_RECEIVED if all lines done
        ScPurchaseOrder po = getPurchaseOrder(tenantId, gr.getPurchaseOrderId());
        long notReceived = poLineRepo.countNotFullyReceived(po.getId());
        if (notReceived == 0) {
            po.fullyReceive();
            log.info("[SCM] PO {} now FULLY_RECEIVED", po.getOrderNumber());
        } else {
            po.partiallyReceive();
        }
        poRepo.save(po);

        log.info("[SCM] GR {} posted — {} lines, PO status → {}", gr.getReceiptNumber(), lines.size(), po.getStatus());
        return gr;
    }

    // ── Supplier Invoices ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ScSupplierInvoice> getSupplierInvoices(TenantId tenantId, String status, Pageable pageable) {
        UUID tid = tenantId.getValue();
        if (status != null && !status.isBlank()) {
            InvoiceStatus statusEnum = InvoiceStatus.valueOf(status.toUpperCase());
            return invoiceRepo.findByTenantIdAndStatus(tid, statusEnum, pageable);
        }
        return invoiceRepo.findByTenantId(tid, pageable);
    }

    /**
     * Creates a supplier invoice and performs real 3-way matching.
     *
     * FIX C-1: invoice number from SequenceService — atomic.
     * FIX H-3: real 3-way match compares invoice amount to PO amount within tolerance.
     */
    @Transactional
    public ScSupplierInvoice createSupplierInvoice(TenantId tenantId, CreateSupplierInvoiceRequest req) {
        UUID tid = tenantId.getValue();

        // FIX C-1: atomic sequence
        int seq = sequenceService.next(tid, "SINV");
        String invoiceNumber = "SINV-" + String.format("%05d", seq);

        ScSupplierInvoice inv = ScSupplierInvoice.create(tid, invoiceNumber,
                req.supplierId(), req.purchaseOrderId(), req.goodsReceiptId(),
                req.supplierInvoiceRef(), req.invoiceDate(), req.dueDate(),
                req.currency(), req.subtotal(), req.vatAmount(), req.totalAmount(),
                req.notes());

        // FIX H-3: real 3-way match
        if (req.purchaseOrderId() != null) {
            performThreeWayMatch(tenantId, inv, req.purchaseOrderId(), req.goodsReceiptId());
        }

        ScSupplierInvoice saved = invoiceRepo.save(inv);
        log.info("[SCM] Supplier invoice {} created matchStatus={}", invoiceNumber, saved.getMatchStatus());
        return saved;
    }

    @Transactional
    public ScSupplierInvoice approveSupplierInvoice(TenantId tenantId, UUID id,
                                                    UUID approverId, String approverName) {
        ScSupplierInvoice inv = invoiceRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Invoice not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
        inv.approve(approverId, approverName);
        log.info("[SCM] Supplier invoice {} approved by {}", inv.getInvoiceNumber(), approverName);
        return invoiceRepo.save(inv);
    }

    @Transactional
    public ScSupplierInvoice markPaid(TenantId tenantId, UUID id, String paymentRef) {
        ScSupplierInvoice inv = invoiceRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Invoice not found",
                        HttpStatus.NOT_FOUND, "NOT_FOUND"));
        inv.markPaid(paymentRef);
        return invoiceRepo.save(inv);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void recalculatePoTotals(ScPurchaseOrder po) {
        List<ScPoLine> lines = poLineRepo.findByPurchaseOrderId(po.getId());
        BigDecimal sub = lines.stream().map(ScPoLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vat = lines.stream().map(ScPoLine::getVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        po.recalculateTotals(sub, vat, sub.add(vat));
    }

    /**
     * Real 3-way match logic (FIX H-3).
     *
     * WHY 2% tolerance and not 0%?
     * Rounding differences, currency conversion, and minor pricing adjustments
     * mean exact matches are rare in practice. A 2% tolerance is the industry
     * standard for automated matching — anything larger triggers human review.
     *
     * Match rules:
     *   1. Invoice supplier must match PO supplier.
     *   2. Invoice total must be within ±2% of PO total_amount.
     *   3. If a GR is linked, it must be POSTED (goods physically received).
     *   4. If all checks pass → MATCHED; partial checks → PARTIAL_MATCH; variance > 2% → DISPUTE.
     */
    private void performThreeWayMatch(TenantId tenantId, ScSupplierInvoice invoice,
                                      UUID purchaseOrderId, UUID goodsReceiptId) {
        try {
            ScPurchaseOrder po = poRepo.findByTenantIdAndId(tenantId.getValue(), purchaseOrderId)
                    .orElse(null);
            if (po == null) {
                invoice.updateMatchResult(MatchStatus.PENDING, "Linked PO not found");
                return;
            }

            // Check 1: supplier must match
            if (!po.getSupplierId().equals(invoice.getSupplierId())) {
                invoice.updateMatchResult(MatchStatus.DISPUTE,
                        "Invoice supplier does not match PO supplier");
                return;
            }

            // Check 2: amount variance
            if (po.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal variance = invoice.getTotalAmount().subtract(po.getTotalAmount()).abs();
                BigDecimal toleranceAmount = po.getTotalAmount()
                        .multiply(MATCH_TOLERANCE).setScale(2, RoundingMode.HALF_UP);

                if (variance.compareTo(toleranceAmount) > 0) {
                    String notes = String.format(
                            "Invoice total R %.2f vs PO total R %.2f — variance R %.2f exceeds 2%% tolerance",
                            invoice.getTotalAmount(), po.getTotalAmount(), variance);
                    invoice.updateMatchResult(MatchStatus.DISPUTE, notes);
                    return;
                }
            }

            // Check 3: GR must be posted if provided
            if (goodsReceiptId != null) {
                ScGoodsReceipt gr = grRepo.findByTenantIdAndId(tenantId.getValue(), goodsReceiptId)
                        .orElse(null);
                if (gr == null || !"POSTED".equals(gr.getStatus())) {
                    invoice.updateMatchResult(MatchStatus.PARTIAL_MATCH,
                            "GR linked but not yet posted — goods may not be received");
                    return;
                }
            }

            // All checks passed
            invoice.updateMatchResult(MatchStatus.MATCHED,
                    goodsReceiptId != null ? "PO + GR + invoice all matched within tolerance"
                            : "PO + invoice matched — no GR linked");

        } catch (Exception e) {
            // 3-way match failure must not block invoice creation
            log.warn("[SCM] 3-way match error for invoice={}: {}", invoice.getInvoiceNumber(), e.getMessage());
            invoice.updateMatchResult(MatchStatus.PENDING, "Match evaluation error: " + e.getMessage());
        }
    }
}
