package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityGoodsReceipt;
import za.co.handyflow.platform.facilities.domain.model.FacilityPurchaseOrder;
import za.co.handyflow.platform.facilities.domain.model.FacilityPurchaseOrderLine;
import za.co.handyflow.platform.facilities.domain.model.FacilityStockItem;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendor;
import za.co.handyflow.platform.facilities.domain.repository.FacilityApprovalLimitRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityGoodsReceiptRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityPurchaseOrderRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityStockItemRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityVendorRepository;
import za.co.handyflow.platform.facilities.dto.GoodsReceiptResponse;
import za.co.handyflow.platform.facilities.dto.PurchaseOrderRequest;
import za.co.handyflow.platform.facilities.dto.PurchaseOrderResponse;
import za.co.handyflow.platform.facilities.dto.ReceiveOrderRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Purchase orders for stock.
 * <ul>
 *   <li>Raised against an active vendor, for active stock items; a draft can be edited freely.</li>
 *   <li>Approval needs the vendor to be eligible right now (approved, required documents in force) and someone
 *       other than the creator; an admin may approve their own order, which is audited as such.</li>
 *   <li>A delivery books stock in through {@link FacilityStockService} in one transaction under a lock on the
 *       order: it is all-or-nothing, over-receiving is refused (409 {@code OVER_RECEIPT}), and the status moves
 *       to PARTLY_RECEIVED or RECEIVED. The invoiced unit cost, if different from the order, feeds the item's
 *       average cost.</li>
 *   <li>Approval is also held to the approver's value limit once any limits are set (admins are never limited).</li>
 *   <li>Every delivery produces a goods-receipt document (its own record, numbered), the basis for matching invoices.</li>
 *   <li>An order with nothing received can be cancelled; a part-delivered one is closed short.</li>
 *   <li>Every step is audited.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityPurchaseOrderService {

    private final FacilityPurchaseOrderRepository orderRepository;
    private final FacilityStockItemRepository itemRepository;
    private final FacilityVendorRepository vendorRepository;
    private final FacilityVendorEligibility vendorEligibility;
    private final FacilityStockService stockService;
    private final FacilityNumberGenerator numberGenerator;
    private final FacilityAccessPolicy accessPolicy;
    private final FacilityAuditService auditService;
    private final FacilityGoodsReceiptRepository receiptRepository;
    private final FacilityApprovalLimitRepository limitRepository;

    @Transactional
    public PurchaseOrderResponse create(TenantId tenantId, PurchaseOrderRequest req) {
        FacilityVendor vendor = requireActiveVendor(tenantId, req.vendorId());
        FacilityPurchaseOrder po = FacilityPurchaseOrder.create(tenantId, numberGenerator.nextPurchaseOrderNumber(tenantId),
                vendor.getId(), req.expectedDate(), req.notes(), TenantContext.getCurrentUserId(), specs(tenantId, req));
        orderRepository.save(po);
        auditService.record(tenantId, "PO_CREATED", "PURCHASE_ORDER", po.getId(),
                po.getPoNumber() + " " + vendor.getCompanyName() + " total=" + po.totalValue());
        return toResponse(po, vendor.getCompanyName());
    }

    @Transactional
    public PurchaseOrderResponse update(TenantId tenantId, UUID id, PurchaseOrderRequest req) {
        FacilityPurchaseOrder po = find(tenantId, id);
        FacilityVendor vendor = requireActiveVendor(tenantId, req.vendorId());
        try {
            po.revise(vendor.getId(), req.expectedDate(), req.notes(), specs(tenantId, req));
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "PO_NOT_EDITABLE");
        }
        orderRepository.save(po);
        auditService.record(tenantId, "PO_UPDATED", "PURCHASE_ORDER", id, po.getPoNumber() + " total=" + po.totalValue());
        return toResponse(po, vendor.getCompanyName());
    }

    @Transactional
    public PurchaseOrderResponse approve(TenantId tenantId, UUID id) {
        FacilityPurchaseOrder po = lock(tenantId, id);
        if (!"DRAFT".equals(po.getStatus()))
            throw new HandyFlowException("Only a draft order can be approved — status is " + po.getStatus(), HttpStatus.CONFLICT, "PO_NOT_DRAFT");

        UUID approver = TenantContext.getCurrentUserId();
        boolean ownOrder = approver != null && approver.equals(po.getCreatedByUserId());
        if (ownOrder && !accessPolicy.hasAuthority("FACILITIES_ADMIN"))
            throw new HandyFlowException("You raised this order, so someone else must approve it", HttpStatus.FORBIDDEN, "PO_SELF_APPROVAL");

        String overLimit = FacilityProcurementRules.approvalProblem(accessPolicy.hasAuthority("FACILITIES_ADMIN"),
                limitRepository.anyForTenant(tenantId),
                limitRepository.findForUser(tenantId, approver).map(l -> l.getMaxOrderValue()).orElse(null), po.totalValue());
        if (overLimit != null) throw new HandyFlowException(overLimit, HttpStatus.FORBIDDEN, "PO_ABOVE_LIMIT");

        FacilityVendor vendor = requireActiveVendor(tenantId, po.getVendorId());
        List<String> problems = vendorEligibility.problems(tenantId, vendor);
        if (!problems.isEmpty())
            throw new HandyFlowException("Cannot order from " + vendor.getCompanyName() + ": " + String.join("; ", problems),
                    HttpStatus.CONFLICT, "VENDOR_NOT_ELIGIBLE");

        po.approve(approver);
        orderRepository.save(po);
        auditService.record(tenantId, ownOrder ? "PO_SELF_APPROVED" : "PO_APPROVED", "PURCHASE_ORDER", id,
                po.getPoNumber() + " total=" + po.totalValue());
        return toResponse(po, vendor.getCompanyName());
    }

    @Transactional
    public PurchaseOrderResponse receive(TenantId tenantId, UUID id, ReceiveOrderRequest req) {
        return receive(tenantId, id, req, null);
    }

    /** Book a delivery into {@code storeId} (the default store when null). */
    @Transactional
    public PurchaseOrderResponse receive(TenantId tenantId, UUID id, ReceiveOrderRequest req, UUID storeId) {
        FacilityPurchaseOrder po = lock(tenantId, id);
        if (!po.isOpen())
            throw new HandyFlowException("Goods can only be received against an approved order — status is " + po.getStatus(),
                    HttpStatus.CONFLICT, "PO_NOT_RECEIVABLE");

        Map<UUID, FacilityPurchaseOrderLine> byId = po.getLines().stream()
                .collect(Collectors.toMap(FacilityPurchaseOrderLine::getId, l -> l));
        Set<UUID> seen = new HashSet<>();
        for (ReceiveOrderRequest.Line l : req.lines()) {
            FacilityPurchaseOrderLine line = byId.get(l.lineId());
            if (line == null) throw new IllegalArgumentException("Line " + l.lineId() + " is not on order " + po.getPoNumber());
            if (!seen.add(l.lineId())) throw new IllegalArgumentException("A line can appear once per delivery");
            if (l.quantity().compareTo(line.outstanding()) > 0)
                throw new HandyFlowException("Cannot receive " + l.quantity().stripTrailingZeros().toPlainString() + " of "
                        + line.getDescription() + ": only " + line.outstanding().stripTrailingZeros().toPlainString() + " outstanding",
                        HttpStatus.CONFLICT, "OVER_RECEIPT");
        }

        UUID receivingStore = stockService.resolveStore(tenantId, storeId).getId();
        // Item rows are locked in a fixed order so two deliveries touching the same items cannot deadlock.
        List<ReceiveOrderRequest.Line> ordered = new ArrayList<>(req.lines());
        ordered.sort(Comparator.comparing(l -> byId.get(l.lineId()).getItemId()));
        String reference = (req.reference() == null || req.reference().isBlank() ? "" : req.reference().trim() + " · ") + po.getPoNumber();
        StringBuilder summary = new StringBuilder();
        List<FacilityGoodsReceipt.LineSpec> receiptLines = new ArrayList<>();
        for (ReceiveOrderRequest.Line l : ordered) {
            FacilityPurchaseOrderLine line = byId.get(l.lineId());
            BigDecimal cost = l.unitCost() != null ? l.unitCost() : line.getUnitCost();
            stockService.receiveAgainstOrder(tenantId, line.getItemId(), l.quantity(), cost, po.getId(), reference, receivingStore);
            po.receiveLine(line.getId(), l.quantity());
            receiptLines.add(new FacilityGoodsReceipt.LineSpec(line.getId(), line.getItemId(), line.getDescription(), l.quantity(), cost));
            summary.append(' ').append(line.getDescription()).append(" +").append(l.quantity().stripTrailingZeros().toPlainString());
        }
        orderRepository.save(po);
        FacilityGoodsReceipt receipt = FacilityGoodsReceipt.create(tenantId, numberGenerator.nextGoodsReceiptNumber(tenantId), po.getId(),
                po.getVendorId(), receivingStore, req.reference(), FacilityTime.today(), TenantContext.getCurrentUserId(), receiptLines);
        receiptRepository.save(receipt);
        auditService.record(tenantId, "PO_RECEIVED", "PURCHASE_ORDER", id,
                po.getPoNumber() + " " + receipt.getGrnNumber() + " -> " + po.getStatus() + ":" + summary);
        return toResponse(po, vendorName(tenantId, po.getVendorId()));
    }

    @Transactional
    public PurchaseOrderResponse cancel(TenantId tenantId, UUID id, String reason) {
        FacilityPurchaseOrder po = lock(tenantId, id);
        try {
            po.cancel(reason);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "PO_NOT_CANCELLABLE");
        }
        orderRepository.save(po);
        auditService.record(tenantId, "PO_CANCELLED", "PURCHASE_ORDER", id, po.getPoNumber() + ": " + reason.trim());
        return toResponse(po, vendorName(tenantId, po.getVendorId()));
    }

    @Transactional
    public PurchaseOrderResponse closeShort(TenantId tenantId, UUID id, String reason) {
        FacilityPurchaseOrder po = lock(tenantId, id);
        try {
            po.closeShort(reason);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "PO_NOT_CLOSABLE");
        }
        orderRepository.save(po);
        auditService.record(tenantId, "PO_CLOSED_SHORT", "PURCHASE_ORDER", id, po.getPoNumber() + ": " + reason.trim());
        return toResponse(po, vendorName(tenantId, po.getVendorId()));
    }

    @Transactional(readOnly = true)
    public List<GoodsReceiptResponse> goodsReceipts(TenantId tenantId, UUID id) {
        find(tenantId, id);
        return receiptRepository.findForPurchaseOrder(tenantId, id).stream().map(FacilityPurchaseOrderService::toResponse).toList();
    }

    static GoodsReceiptResponse toResponse(FacilityGoodsReceipt r) {
        return new GoodsReceiptResponse(r.getId(), r.getGrnNumber(), r.getPurchaseOrderId(), r.getVendorId(), r.getStoreId(), r.getDeliveryNote(),
                r.getReceivedOn(), r.getTotalValue(), r.getReceivedByUserId(), r.getCreatedAt(),
                r.getLines().stream().map(l -> new GoodsReceiptResponse.Line(l.getPoLineId(), l.getItemId(), l.getDescription(),
                        l.getQuantity(), l.getUnitCost(), l.lineValue())).toList());
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(TenantId tenantId, UUID id) {
        FacilityPurchaseOrder po = find(tenantId, id);
        return toResponse(po, vendorName(tenantId, po.getVendorId()));
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> list(TenantId tenantId, String status, Pageable pageable) {
        String s = status == null || status.isBlank() ? "ALL" : status.trim().toUpperCase();
        Page<FacilityPurchaseOrder> page = orderRepository.list(tenantId, s, pageable);
        Map<UUID, String> names = new HashMap<>();
        Set<UUID> vendorIds = page.getContent().stream().map(FacilityPurchaseOrder::getVendorId).collect(Collectors.toSet());
        if (!vendorIds.isEmpty())
            vendorRepository.findActiveByIds(tenantId, vendorIds).forEach(v -> names.put(v.getId(), v.getCompanyName()));
        return page.map(po -> toResponse(po, names.get(po.getVendorId())));
    }

    // ── internals ───────────────────────────────────────────────────────────────────────────

    private List<FacilityPurchaseOrder.LineSpec> specs(TenantId tenantId, PurchaseOrderRequest req) {
        List<UUID> ids = req.lines().stream().map(PurchaseOrderRequest.Line::itemId).distinct().toList();
        Map<UUID, FacilityStockItem> items = itemRepository.findByIds(tenantId, ids).stream()
                .collect(Collectors.toMap(FacilityStockItem::getId, i -> i));
        List<FacilityPurchaseOrder.LineSpec> out = new ArrayList<>();
        for (PurchaseOrderRequest.Line l : req.lines()) {
            FacilityStockItem item = items.get(l.itemId());
            if (item == null) throw new ResourceNotFoundException("FacilityStockItem", l.itemId().toString());
            if (!item.isActive())
                throw new HandyFlowException(item.getSku() + " is inactive", HttpStatus.CONFLICT, "STOCK_ITEM_INACTIVE");
            out.add(new FacilityPurchaseOrder.LineSpec(item.getId(), item.getSku() + " " + item.getName(), l.quantity(), l.unitCost()));
        }
        return out;
    }

    private FacilityVendor requireActiveVendor(TenantId tenantId, UUID vendorId) {
        FacilityVendor v = vendorRepository.findActiveById(tenantId, vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityVendor", vendorId.toString()));
        if (!v.isActive())
            throw new HandyFlowException(v.getCompanyName() + " is inactive", HttpStatus.CONFLICT, "VENDOR_INACTIVE");
        return v;
    }

    private String vendorName(TenantId tenantId, UUID vendorId) {
        return vendorRepository.findActiveById(tenantId, vendorId).map(FacilityVendor::getCompanyName).orElse(null);
    }

    private FacilityPurchaseOrder find(TenantId tenantId, UUID id) {
        return orderRepository.findForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityPurchaseOrder", id.toString()));
    }

    private FacilityPurchaseOrder lock(TenantId tenantId, UUID id) {
        return orderRepository.findForUpdate(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityPurchaseOrder", id.toString()));
    }

    private PurchaseOrderResponse toResponse(FacilityPurchaseOrder po, String vendorName) {
        List<PurchaseOrderResponse.Line> lines = po.getLines().stream()
                .map(l -> new PurchaseOrderResponse.Line(l.getId(), l.getLineNo(), l.getItemId(), l.getDescription(),
                        l.getOrderedQty(), l.getReceivedQty(), l.outstanding(), l.getUnitCost(), l.lineTotal()))
                .toList();
        return new PurchaseOrderResponse(po.getId(), po.getPoNumber(), po.getVendorId(), vendorName, po.getStatus(),
                po.getExpectedDate(), po.getNotes(), po.totalValue(), po.getCreatedByUserId(), po.getApprovedByUserId(),
                po.getApprovedAt(), po.getClosedReason(), po.getCreatedAt(), lines);
    }
}
