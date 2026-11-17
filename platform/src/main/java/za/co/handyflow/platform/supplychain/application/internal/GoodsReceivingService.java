package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;
import za.co.handyflow.platform.supplychain.domain.enums.InvoiceStatus;
import za.co.handyflow.platform.supplychain.domain.model.*;
import za.co.handyflow.platform.supplychain.domain.repository.*;
import za.co.handyflow.platform.supplychain.dto.CreateGoodsReceiptRequest;
import za.co.handyflow.platform.supplychain.dto.GoodsReceiptLineResponse;
import za.co.handyflow.platform.supplychain.dto.PostGrLineRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Controlled goods receiving: create, post (with inspection / partial / rejected quantities
 * and over-receipt protection) and reverse. Extracted from ScmService, which now delegates.
 * <p>
 * Semantics:
 *  - qtyReceived = delivered; qtyRejected = refused; accepted = received - rejected.
 *  - Only ACCEPTED quantity enters stock, counts against the PO line, and is what invoice
 *    matching compares against.
 *  - Every line must resolve to a PO line (no silent "unordered" receipts).
 *  - A posted GR is never edited or deleted — it is reversed (offsetting stock movements,
 *    PO quantities rolled back, reason + actor recorded).
 * Lock order (avoid deadlocks): goods receipt row, then purchase order row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsReceivingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ROUNDING = new BigDecimal("0.10");

    private final ScGoodsReceiptRepository grRepo;
    private final ScGrLineRepository grLineRepo;
    private final ScPurchaseOrderRepository poRepo;
    private final ScPoLineRepository poLineRepo;
    private final ScInventoryRepository inventoryRepo;
    private final ScStockMovementRepository movementRepo;
    private final ScStockLocationRepository locationRepo;
    private final ScSupplierInvoiceRepository invoiceRepo;
    private final ScSupplierInvoiceLineRepository invoiceLineRepo;
    private final TenantSequenceService sequenceService;
    private final ControlExceptionFacade controlExceptionFacade;

    /** app.scm.receiving.over-receipt-tolerance-pct — accepted qty may exceed ordered qty by this %. Default 0 (strict). */
    @Value("${app.scm.receiving.over-receipt-tolerance-pct:0.0}")
    private BigDecimal overReceiptTolerancePct;

    /** Highest cumulative accepted quantity allowed for a PO line. */
    public static BigDecimal maxReceivable(BigDecimal ordered, BigDecimal tolerancePct) {
        return ordered.multiply(BigDecimal.ONE.add(tolerancePct.divide(HUNDRED, 6, RoundingMode.HALF_UP)));
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public ScGoodsReceipt create(TenantId tenantId, UUID userId, String userName, CreateGoodsReceiptRequest req) {
        UUID tid = tenantId.getValue();
        if (req.purchaseOrderId() == null) throw bad("purchaseOrderId is required");
        ScPurchaseOrder po = poRepo.findByTenantIdAndId(tid, req.purchaseOrderId())
                .orElseThrow(() -> new HandyFlowException("Purchase order not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!po.canReceive())
            throw new HandyFlowException("PO is not in a receivable state — current: " + po.getStatus(),
                    HttpStatus.BAD_REQUEST, "INVALID_STATE");

        // received_to is NOT NULL and previously unvalidated: a missing or foreign location
        // either crashed at insert or let stock land in another tenant's location.
        UUID locationId = req.receivedToLocation();
        if (locationId == null) {
            locationId = locationRepo.findDefaultByTenantId(tid).map(ScStockLocation::getId)
                    .orElseThrow(() -> bad("No receiving location supplied and no default stock location is configured"));
        } else {
            locationRepo.findActiveByTenantIdAndId(tid, locationId)
                    .orElseThrow(() -> new HandyFlowException("Receiving location not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        }

        long seq = sequenceService.nextValue(tenantId, "GR");
        String receiptNumber = "GR-" + String.format("%05d", seq);

        return grRepo.save(ScGoodsReceipt.create(tid, receiptNumber, po.getId(), po.getSupplierId(),
                locationId, req.deliveryNoteRef(), userId, userName,
                req.receivedDate() != null ? req.receivedDate() : LocalDate.now(), req.notes()));
    }

    // ── Post ──────────────────────────────────────────────────────────────────

    @Transactional
    public ScGoodsReceipt post(TenantId tenantId, UUID grId, UUID userId, String userName,
                               List<PostGrLineRequest> lines) {
        UUID tid = tenantId.getValue();
        ScGoodsReceipt gr = grRepo.lockByTenantIdAndId(tid, grId)
                .orElseThrow(() -> new HandyFlowException("Goods receipt not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!ScGoodsReceipt.DRAFT.equals(gr.getStatus()))
            throw new HandyFlowException("Goods receipt " + gr.getReceiptNumber() + " is already " + gr.getStatus(),
                    HttpStatus.CONFLICT, "GR_ALREADY_POSTED");
        if (lines == null || lines.isEmpty())
            throw new HandyFlowException("Cannot post a goods receipt with no lines", HttpStatus.BAD_REQUEST, "NO_LINES");

        ScPurchaseOrder po = poRepo.lockByTenantIdAndId(tid, gr.getPurchaseOrderId())
                .orElseThrow(() -> new HandyFlowException("Purchase order not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!po.canReceive())   // e.g. cancelled after this GR was drafted
            throw new HandyFlowException("PO " + po.getOrderNumber() + " is " + po.getStatus() + " and can no longer receive goods",
                    HttpStatus.CONFLICT, "INVALID_STATE");

        List<ScPoLine> poLines = poLineRepo.findByPurchaseOrderId(po.getId());
        int processed = 0;
        List<String> rejections = new ArrayList<>();

        for (PostGrLineRequest line : lines) {
            BigDecimal received = line.qtyReceived();
            if (received == null || received.compareTo(BigDecimal.ZERO) == 0) continue;
            if (received.compareTo(BigDecimal.ZERO) < 0) throw bad("qtyReceived cannot be negative");

            BigDecimal rejected = line.qtyRejected() != null ? line.qtyRejected() : BigDecimal.ZERO;
            if (rejected.compareTo(BigDecimal.ZERO) < 0 || rejected.compareTo(received) > 0)
                throw bad("qtyRejected must be between 0 and qtyReceived");
            if (rejected.compareTo(BigDecimal.ZERO) > 0
                    && (line.rejectionReason() == null || line.rejectionReason().isBlank()))
                throw bad("A rejectionReason is required when goods are rejected");
            BigDecimal accepted = received.subtract(rejected);

            ScPoLine poLine = resolvePoLine(poLines, line);

            BigDecimal limit = maxReceivable(poLine.getQtyOrdered(), overReceiptTolerancePct);
            if (poLine.getQtyReceived().add(accepted).compareTo(limit) > 0) {
                throw new HandyFlowException(poLine.getItemName() + ": accepting " + accepted
                        + " would take the received total to " + poLine.getQtyReceived().add(accepted)
                        + ", above the ordered quantity of " + poLine.getQtyOrdered()
                        + (overReceiptTolerancePct.signum() > 0 ? " (+" + overReceiptTolerancePct + "% tolerance)" : "")
                        + ". Reject the excess or amend the PO.",
                        HttpStatus.CONFLICT, "OVER_RECEIPT");
            }

            BigDecimal unitCost = line.unitCost() != null && line.unitCost().signum() > 0
                    ? line.unitCost() : poLine.getUnitCost();
            UUID catItemId = line.catalogueItemId() != null ? line.catalogueItemId() : poLine.getCatalogueItemId();
            String condition = line.condition() != null && !line.condition().isBlank() ? line.condition()
                    : rejected.signum() > 0 ? "DAMAGED" : "GOOD";

            grLineRepo.save(ScGrLine.createInspected(tid, gr.getId(), poLine.getId(), catItemId,
                    poLine.getItemName(), poLine.getQtyOrdered(), received, rejected,
                    line.rejectionReason(), unitCost, condition, line.lotNumber(), line.expiryDate()));

            if (accepted.signum() > 0) {
                poLine.recordReceived(accepted);
                poLineRepo.save(poLine);
                // Lines with no catalogue item (services / non-stock) are receipted against the
                // PO but create no inventory movement — there is no stock record to update.
                if (catItemId != null) {
                    inventoryRepo.upsertInventoryRow(UUID.randomUUID(), tid, gr.getReceivedTo(), catItemId);
                    ScInventory inv = inventoryRepo.findByTenantIdAndLocationIdAndCatalogueItemId(
                            tid, gr.getReceivedTo(), catItemId).orElseThrow();
                    ScStockMovement mv = ScStockMovement.record(tid, inv, "PURCHASE", accepted, unitCost,
                            "GOODS_RECEIPT", gr.getId(), gr.getReceiptNumber(), userId, userName);
                    inv.adjustQty(accepted, unitCost);
                    inventoryRepo.save(inv);
                    movementRepo.save(mv);
                }
            }
            if (rejected.signum() > 0) {
                rejections.add(poLine.getItemName() + " x" + rejected.stripTrailingZeros().toPlainString()
                        + " (" + line.rejectionReason() + ")");
            }
            processed++;
        }
        if (processed == 0)
            throw new HandyFlowException("No receipt line had a quantity", HttpStatus.BAD_REQUEST, "NO_LINES");

        gr.post();
        gr = grRepo.save(gr);

        if (poLineRepo.countNotFullyReceived(po.getId()) == 0) {
            po.fullyReceive();
            log.info("[SCM] PO {} now FULLY_RECEIVED", po.getOrderNumber());
        } else {
            po.partiallyReceive();
        }
        poRepo.save(po);

        if (!rejections.isEmpty()) {
            controlExceptionFacade.raise(tenantId, "supplychain", "GRN_REJECTION", "ScGoodsReceipt", gr.getId(),
                    "WARNING", "Goods rejected on " + gr.getReceiptNumber() + " (" + po.getOrderNumber() + "): "
                            + String.join("; ", rejections));
        }
        log.info("[SCM] GR {} posted — {} line(s), {} rejection(s), PO status → {}",
                gr.getReceiptNumber(), processed, rejections.size(), po.getStatus());
        return gr;
    }

    // ── Reverse ───────────────────────────────────────────────────────────────

    /**
     * Reverses a POSTED receipt. Refused (409) when it would leave the books inconsistent:
     * a non-cancelled invoice links the GR; accepted-after-reversal would drop below what has
     * already been invoiced; or the stock has already been used (on-hand would go negative).
     * Cancel or re-match the invoice first, or correct stock, then reverse.
     */
    @Transactional
    public ScGoodsReceipt reverse(TenantId tenantId, UUID grId, UUID userId, String userName, String reason) {
        UUID tid = tenantId.getValue();
        if (reason == null || reason.isBlank())
            throw new HandyFlowException("A reversal reason is required", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");

        ScGoodsReceipt gr = grRepo.lockByTenantIdAndId(tid, grId)
                .orElseThrow(() -> new HandyFlowException("Goods receipt not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!ScGoodsReceipt.POSTED.equals(gr.getStatus()))
            throw new HandyFlowException("Only POSTED receipts can be reversed — " + gr.getReceiptNumber() + " is " + gr.getStatus(),
                    HttpStatus.CONFLICT, "GR_NOT_REVERSIBLE");

        ScPurchaseOrder po = poRepo.lockByTenantIdAndId(tid, gr.getPurchaseOrderId())
                .orElseThrow(() -> new HandyFlowException("Purchase order not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        if (grRepo.countInvoicesLinkedToReceipt(tid, grId, InvoiceStatus.CANCELLED) > 0)
            throw new HandyFlowException("A supplier invoice is linked to " + gr.getReceiptNumber()
                    + " — cancel it before reversing the receipt", HttpStatus.CONFLICT, "GR_INVOICED");

        List<ScGrLine> grLines = grLineRepo.findByGoodsReceiptId(grId);
        List<ScPoLine> poLines = poLineRepo.findByPurchaseOrderId(po.getId());

        // Accepted quantity per PO line that would remain once this GR is removed.
        Map<UUID, BigDecimal> remaining = toMap(grLineRepo.sumAcceptedByPoLine(tid, po.getId()));
        Map<UUID, BigDecimal> thisGr = new HashMap<>();
        for (ScGrLine l : grLines) thisGr.merge(l.getPoLineId(), l.getQtyAccepted(), BigDecimal::add);
        thisGr.forEach((poLineId, qty) -> remaining.merge(poLineId, qty.negate(), BigDecimal::add));

        Map<UUID, BigDecimal> invoiced = toMap(invoiceLineRepo.sumPriorInvoicedByPoLine(
                tid, po.getId(), UUID.randomUUID(), InvoiceStatus.CANCELLED));
        for (Map.Entry<UUID, BigDecimal> e : invoiced.entrySet()) {
            if (e.getValue().compareTo(remaining.getOrDefault(e.getKey(), BigDecimal.ZERO)) > 0)
                throw new HandyFlowException("Reversal blocked: invoices already claim " + e.getValue().stripTrailingZeros().toPlainString()
                        + " of a PO line that would only have "
                        + remaining.getOrDefault(e.getKey(), BigDecimal.ZERO).stripTrailingZeros().toPlainString()
                        + " accepted. Cancel or correct the invoice first.", HttpStatus.CONFLICT, "GR_INVOICED");
        }
        // Header-only invoices (no lines) are covered by a value check.
        BigDecimal invoicedTotal = invoiceRepo.sumInvoicedForPo(tid, po.getId(), UUID.randomUUID(), InvoiceStatus.CANCELLED);
        if (invoicedTotal != null && invoicedTotal.signum() > 0) {
            BigDecimal valueAfter = BigDecimal.ZERO;
            for (ScPoLine l : poLines) {
                BigDecimal net = remaining.getOrDefault(l.getId(), BigDecimal.ZERO).multiply(l.getUnitCost());
                valueAfter = valueAfter.add(net.add(net.multiply(l.getVatRate()).divide(HUNDRED, 6, RoundingMode.HALF_UP)));
            }
            if (invoicedTotal.compareTo(valueAfter.add(ROUNDING)) > 0)
                throw new HandyFlowException("Reversal blocked: R " + invoicedTotal.setScale(2, RoundingMode.HALF_UP)
                        + " is already invoiced against " + po.getOrderNumber() + " but only R "
                        + valueAfter.setScale(2, RoundingMode.HALF_UP) + " would remain received.",
                        HttpStatus.CONFLICT, "GR_INVOICED");
        }

        // Offsetting stock movements (history preserved — nothing is deleted).
        for (ScStockMovement m : movementRepo.findByReference("GOODS_RECEIPT", grId)) {
            if (!tid.equals(m.getTenantId()) || m.getQtyChange().signum() <= 0) continue;
            ScInventory inv = inventoryRepo.findByTenantIdAndId(tid, m.getInventoryId())
                    .orElseThrow(() -> new HandyFlowException("Inventory record not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
            BigDecimal delta = m.getQtyChange().negate();
            if (inv.getQtyOnHand().add(delta).signum() < 0)
                throw new HandyFlowException("Reversal blocked: " + m.getQtyChange().stripTrailingZeros().toPlainString()
                        + " was received but only " + inv.getQtyOnHand().stripTrailingZeros().toPlainString()
                        + " is on hand — the stock has already been used. Adjust stock first.",
                        HttpStatus.CONFLICT, "STOCK_CONSUMED");
            ScStockMovement rev = ScStockMovement.record(tid, inv, "PURCHASE_REVERSAL", delta, m.getUnitCost(),
                    "GR_REVERSAL", gr.getId(), gr.getReceiptNumber(), userId, userName);
            inv.adjustQty(delta, m.getUnitCost());
            inventoryRepo.save(inv);
            movementRepo.save(rev);
        }

        Map<UUID, ScPoLine> byId = new HashMap<>();
        poLines.forEach(l -> byId.put(l.getId(), l));
        thisGr.forEach((poLineId, qty) -> {
            ScPoLine l = byId.get(poLineId);
            if (l != null && qty.signum() > 0) { l.reverseReceived(qty); poLineRepo.save(l); }
        });

        gr.reverse(userId, reason.trim());
        gr = grRepo.save(gr);

        boolean anyReceived = poLines.stream().anyMatch(l -> l.getQtyReceived().signum() > 0);
        po.revertReceiptStatus(anyReceived);
        poRepo.save(po);

        log.info("[SCM] GR {} reversed by {} — PO {} now {} (reason: {})",
                gr.getReceiptNumber(), userName, po.getOrderNumber(), po.getStatus(), reason);
        return gr;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GoodsReceiptLineResponse> listLines(TenantId tenantId, UUID grId) {
        grRepo.findByTenantIdAndId(tenantId.getValue(), grId)
                .orElseThrow(() -> new HandyFlowException("Goods receipt not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        return grLineRepo.findByGoodsReceiptId(grId).stream()
                .map(l -> new GoodsReceiptLineResponse(l.getId(), l.getPoLineId(), l.getCatalogueItemId(),
                        l.getItemName(), l.getQtyOrdered(), l.getQtyReceived(), l.getQtyRejected(),
                        l.getQtyAccepted(), l.getRejectionReason(), l.getCondition(), l.getUnitCost(),
                        l.getLotNumber(), l.getExpiryDate()))
                .toList();
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private ScPoLine resolvePoLine(List<ScPoLine> poLines, PostGrLineRequest line) {
        if (line.poLineId() != null) {
            return poLines.stream().filter(l -> l.getId().equals(line.poLineId())).findFirst()
                    .orElseThrow(() -> bad("PO line " + line.poLineId() + " is not on this receipt's purchase order"));
        }
        if (line.catalogueItemId() != null) {
            List<ScPoLine> matches = poLines.stream()
                    .filter(l -> line.catalogueItemId().equals(l.getCatalogueItemId())).toList();
            // Prefer a line that still has quantity outstanding when an item appears twice.
            for (ScPoLine l : matches) if (!l.isFullyReceived()) return l;
            if (!matches.isEmpty()) return matches.get(0);
        }
        throw bad("Every receipt line needs a poLineId (or a catalogueItemId that is on the purchase order)");
    }

    private static Map<UUID, BigDecimal> toMap(List<Object[]> rows) {
        Map<UUID, BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) m.put((UUID) r[0], (BigDecimal) r[1]);
        return m;
    }

    private static HandyFlowException bad(String msg) {
        return new HandyFlowException(msg, HttpStatus.BAD_REQUEST, "INVALID_RECEIPT_LINE");
    }
}
