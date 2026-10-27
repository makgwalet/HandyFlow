package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.*;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.PurchaseOrderMatchResponse;
import za.co.handyflow.platform.facilities.dto.VendorInvoiceRequest;
import za.co.handyflow.platform.facilities.dto.VendorInvoiceResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Supplier invoices against purchase orders, with the three-way match (ordered, received, invoiced).
 * <ul>
 *   <li>An invoice can only be recorded against an order that has been approved; the vendor comes from the order and the
 *       same invoice number cannot be entered twice for a vendor (409 {@code INVOICE_EXISTS}).</li>
 *   <li>Approving needs someone other than the person who captured it (an admin may approve their own, audited as such).</li>
 *   <li>If the approved invoices would now exceed what was received, or nothing was received, approval needs a written reason and
 *       is audited as an approval with variance. Billing less than received is normal and needs none.</li>
 *   <li>A disputed invoice stops counting toward the invoiced total until it is approved.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FacilityVendorInvoiceService {

    private final FacilityVendorInvoiceRepository invoiceRepository;
    private final FacilityPurchaseOrderRepository orderRepository;
    private final FacilityGoodsReceiptRepository receiptRepository;
    private final FacilityVendorRepository vendorRepository;
    private final FacilityAccessPolicy accessPolicy;
    private final FacilityAuditService auditService;

    @Transactional
    public VendorInvoiceResponse record(TenantId tenantId, VendorInvoiceRequest req) {
        FacilityPurchaseOrder po = findOrder(tenantId, req.purchaseOrderId());
        if (!Set.of("APPROVED", "PARTLY_RECEIVED", "RECEIVED").contains(po.getStatus()))
            throw new HandyFlowException("Invoices can only be recorded against an approved order — status is " + po.getStatus(),
                    HttpStatus.CONFLICT, "PO_NOT_INVOICEABLE");
        if (invoiceRepository.existsForVendor(tenantId, po.getVendorId(), req.invoiceNumber().trim()))
            throw new HandyFlowException("Invoice " + req.invoiceNumber().trim() + " has already been recorded for this vendor",
                    HttpStatus.CONFLICT, "INVOICE_EXISTS");
        FacilityVendorInvoice inv = FacilityVendorInvoice.create(tenantId, po.getId(), po.getVendorId(), req.invoiceNumber(),
                req.invoiceDate(), req.subtotal(), req.vatAmount(), TenantContext.getCurrentUserId());
        invoiceRepository.save(inv);
        auditService.record(tenantId, "INVOICE_RECORDED", "VENDOR_INVOICE", inv.getId(),
                po.getPoNumber() + " invoice=" + inv.getInvoiceNumber() + " subtotal=" + inv.getSubtotal());
        return toResponse(inv, po.getPoNumber(), vendorName(tenantId, po.getVendorId()));
    }

    @Transactional
    public VendorInvoiceResponse approve(TenantId tenantId, UUID id, String note) {
        FacilityVendorInvoice inv = find(tenantId, id);
        if ("APPROVED".equals(inv.getStatus())) throw new HandyFlowException("This invoice is already approved", HttpStatus.CONFLICT, "INVOICE_DECIDED");
        UUID approver = TenantContext.getCurrentUserId();
        boolean own = approver != null && approver.equals(inv.getRecordedByUserId());
        if (own && !accessPolicy.hasAuthority("FACILITIES_ADMIN"))
            throw new HandyFlowException("You captured this invoice, so someone else must approve it", HttpStatus.FORBIDDEN, "INVOICE_SELF_APPROVAL");

        FacilityPurchaseOrder po = findOrder(tenantId, inv.getPurchaseOrderId());
        FacilityProcurementRules.Match match = matchFor(tenantId, po, inv);
        boolean withVariance = match.needsExplanation();
        if (withVariance && (note == null || note.isBlank()))
            throw new HandyFlowException("This order has been invoiced for R" + match.invoiced().toPlainString() + " against R"
                    + match.received().toPlainString() + " received. Say why it should be approved",
                    HttpStatus.CONFLICT, "INVOICE_VARIANCE_NOTE_REQUIRED");
        inv.approve(approver, note);
        invoiceRepository.save(inv);
        auditService.record(tenantId, withVariance ? "INVOICE_APPROVED_WITH_VARIANCE" : own ? "INVOICE_SELF_APPROVED" : "INVOICE_APPROVED",
                "VENDOR_INVOICE", id, po.getPoNumber() + " invoice=" + inv.getInvoiceNumber() + " match=" + match.status()
                        + " variance=" + match.variance().toPlainString() + (note == null || note.isBlank() ? "" : " note=" + note.trim()));
        return toResponse(inv, po.getPoNumber(), vendorName(tenantId, inv.getVendorId()));
    }

    @Transactional
    public VendorInvoiceResponse dispute(TenantId tenantId, UUID id, String reason) {
        FacilityVendorInvoice inv = find(tenantId, id);
        try {
            inv.dispute(TenantContext.getCurrentUserId(), reason);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVOICE_DECIDED");
        }
        invoiceRepository.save(inv);
        FacilityPurchaseOrder po = findOrder(tenantId, inv.getPurchaseOrderId());
        auditService.record(tenantId, "INVOICE_DISPUTED", "VENDOR_INVOICE", id,
                po.getPoNumber() + " invoice=" + inv.getInvoiceNumber() + ": " + reason.trim());
        return toResponse(inv, po.getPoNumber(), vendorName(tenantId, inv.getVendorId()));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderMatchResponse match(TenantId tenantId, UUID purchaseOrderId) {
        FacilityPurchaseOrder po = findOrder(tenantId, purchaseOrderId);
        List<FacilityVendorInvoice> invoices = invoiceRepository.findForPurchaseOrder(tenantId, purchaseOrderId);
        FacilityProcurementRules.Match m = evaluate(tenantId, po, invoices.stream().filter(FacilityVendorInvoice::counts).toList());
        String vendor = vendorName(tenantId, po.getVendorId());
        return new PurchaseOrderMatchResponse(po.getId(), po.getPoNumber(), m.ordered(), m.received(), m.invoiced(), m.variance(),
                m.tolerance(), m.status(), invoices.stream().map(i -> toResponse(i, po.getPoNumber(), vendor)).toList());
    }

    @Transactional(readOnly = true)
    public Page<VendorInvoiceResponse> list(TenantId tenantId, String status, Pageable pageable) {
        String s = status == null || status.isBlank() ? "ALL" : status.trim().toUpperCase();
        Page<FacilityVendorInvoice> page = invoiceRepository.list(tenantId, s, pageable);
        Map<UUID, String> poNumbers = new HashMap<>();
        Map<UUID, String> vendors = new HashMap<>();
        Set<UUID> vendorIds = page.getContent().stream().map(FacilityVendorInvoice::getVendorId).collect(Collectors.toSet());
        if (!vendorIds.isEmpty()) vendorRepository.findActiveByIds(tenantId, vendorIds).forEach(v -> vendors.put(v.getId(), v.getCompanyName()));
        return page.map(i -> toResponse(i, poNumbers.computeIfAbsent(i.getPurchaseOrderId(),
                pid -> orderRepository.findForTenant(tenantId, pid).map(FacilityPurchaseOrder::getPoNumber).orElse(null)), vendors.get(i.getVendorId())));
    }

    // ── internals ────────────────────────────────────────────────────────────

    /**
     * The order's match as it would stand if {@code candidate} were approved: what is already approved plus this invoice.
     * Other invoices still awaiting a decision do not count against it, so approving in any order gives the same total.
     */
    private FacilityProcurementRules.Match matchFor(TenantId tenantId, FacilityPurchaseOrder po, FacilityVendorInvoice candidate) {
        List<FacilityVendorInvoice> counted = new ArrayList<>(invoiceRepository.findForPurchaseOrder(tenantId, po.getId()).stream()
                .filter(i -> "APPROVED".equals(i.getStatus()) && !i.getId().equals(candidate.getId())).toList());
        counted.add(candidate);
        return evaluate(tenantId, po, counted);
    }

    private FacilityProcurementRules.Match evaluate(TenantId tenantId, FacilityPurchaseOrder po, List<FacilityVendorInvoice> counted) {
        List<FacilityGoodsReceiptLine> receiptLines = receiptRepository.findForPurchaseOrder(tenantId, po.getId()).stream()
                .flatMap(r -> r.getLines().stream()).toList();
        BigDecimal invoiced = counted.stream().map(FacilityVendorInvoice::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return FacilityProcurementRules.evaluate(po.totalValue(), FacilityProcurementRules.receivedValue(po.getLines(), receiptLines), invoiced);
    }

    private FacilityVendorInvoice find(TenantId tenantId, UUID id) {
        return invoiceRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityVendorInvoice", id.toString()));
    }

    private FacilityPurchaseOrder findOrder(TenantId tenantId, UUID id) {
        return orderRepository.findForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityPurchaseOrder", id.toString()));
    }

    private String vendorName(TenantId tenantId, UUID vendorId) {
        return vendorRepository.findActiveById(tenantId, vendorId).map(FacilityVendor::getCompanyName).orElse(null);
    }

    static VendorInvoiceResponse toResponse(FacilityVendorInvoice i, String poNumber, String vendorName) {
        return new VendorInvoiceResponse(i.getId(), i.getPurchaseOrderId(), poNumber, i.getVendorId(), vendorName, i.getInvoiceNumber(),
                i.getInvoiceDate(), i.getSubtotal(), i.getVatAmount(), i.total(), i.getStatus(), i.getRecordedByUserId(),
                i.getDecidedByUserId(), i.getDecidedAt(), i.getDecisionNote(), i.getCreatedAt());
    }
}
