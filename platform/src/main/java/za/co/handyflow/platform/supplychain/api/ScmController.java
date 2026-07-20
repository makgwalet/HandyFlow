package za.co.handyflow.platform.supplychain.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.supplychain.application.internal.ScmService;
import za.co.handyflow.platform.supplychain.domain.model.*;
import za.co.handyflow.platform.supplychain.dto.*;

import java.util.List;
import java.util.UUID;

/**
 * Supply Chain REST controller.
 *
 * WHY @Validated at the class level?
 * ────────────────────────────────────
 * @Valid on a @RequestBody triggers Bean Validation on the DTO fields.
 * @Validated on the class enables validation on @RequestParam and @PathVariable too
 * (e.g. @NotNull @PathVariable UUID id). Without @Validated, constraint annotations
 * on method parameters are silently ignored.
 *
 * WHY getCurrentUserName() instead of userId.toString()?
 * ──────────────────────────────────────────────────────
 * `createdByName`, `receivedByName` etc. are displayed to users in movement history
 * and audit logs. A UUID like `3a41cfaf-333a-4b6f-ad76-b282bcb0e701` is meaningless
 * to a warehouse clerk. `TenantContext.getCurrentUserName()` returns "Thabo Molefe"
 * from the firstName/lastName JWT claims — the same value shown in other modules.
 */
@RestController
@RequestMapping("/api/v1/supply-chain")
@RequiredArgsConstructor
@Validated
@Tag(name = "Supply Chain", description = "Suppliers, purchase orders, inventory and AP invoices")
public class ScmController {

    private final ScmService scmService;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "SCM dashboard — supplier count, open POs, pending invoices, low-stock alerts")
    public ResponseEntity<ApiResponse<ScmSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getSummary(TenantContext.getTenantIdAsObject())));
    }

    // ── Suppliers ─────────────────────────────────────────────────────────────

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "List suppliers — filter by status or search by name/email")
    public ResponseEntity<ApiResponse<Page<ScSupplier>>> getSuppliers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getSuppliers(TenantContext.getTenantIdAsObject(), search, status, pageable)));
    }

    @GetMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('SCM_READ')")
    public ResponseEntity<ApiResponse<ScSupplier>> getSupplier(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getSupplier(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAuthority('SCM_ADMIN')")
    @Operation(summary = "Register a new supplier with BBBEE level, banking and payment terms")
    public ResponseEntity<ApiResponse<ScSupplier>> createSupplier(
            @Valid @RequestBody CreateSupplierRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Supplier created",
                scmService.createSupplier(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('SCM_ADMIN')")
    public ResponseEntity<ApiResponse<ScSupplier>> updateSupplier(
            @PathVariable UUID id, @Valid @RequestBody UpdateSupplierRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Supplier updated",
                scmService.updateSupplier(TenantContext.getTenantIdAsObject(), id, req)));
    }

    // ── Supplier items (pricing catalogue) ────────────────────────────────────

    @GetMapping("/suppliers/{id}/items")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "List items this supplier sells and their prices")
    public ResponseEntity<ApiResponse<List<ScSupplierItem>>> getSupplierItems(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getSupplierItems(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/suppliers/{id}/items")
    @PreAuthorize("hasAuthority('SCM_ADMIN')")
    @Operation(summary = "Add an item to a supplier's pricing catalogue")
    public ResponseEntity<ApiResponse<ScSupplierItem>> addSupplierItem(
            @PathVariable UUID id, @Valid @RequestBody AddSupplierItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Supplier item added",
                scmService.addSupplierItem(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @GetMapping("/catalogue-items/{catalogueItemId}/best-price")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Best price across all suppliers for a catalogue item — ordered cheapest first")
    public ResponseEntity<ApiResponse<List<ScSupplierItem>>> getBestPrice(
            @PathVariable UUID catalogueItemId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getBestPriceForItem(TenantContext.getTenantIdAsObject(), catalogueItemId)));
    }

    // ── Stock Locations ───────────────────────────────────────────────────────

    @GetMapping("/locations")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "List active stock locations — warehouses, sites, vehicles")
    public ResponseEntity<ApiResponse<List<ScStockLocation>>> getLocations() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getLocations(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/locations")
    @PreAuthorize("hasAuthority('SCM_INVENTORY')")
    public ResponseEntity<ApiResponse<ScStockLocation>> createLocation(
            @Valid @RequestBody CreateLocationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Location created",
                scmService.createLocation(TenantContext.getTenantIdAsObject(), req)));
    }

    // ── Inventory ─────────────────────────────────────────────────────────────

    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "List inventory — optionally filter by location")
    public ResponseEntity<ApiResponse<List<ScInventory>>> getInventory(
            @RequestParam(required = false) UUID locationId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getInventory(TenantContext.getTenantIdAsObject(), locationId)));
    }

    @GetMapping("/inventory/low-stock")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Items at or below their reorder point")
    public ResponseEntity<ApiResponse<List<ScInventory>>> getLowStock() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getLowStockAlerts(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/inventory/opening")
    @PreAuthorize("hasAuthority('SCM_INVENTORY')")
    @Operation(summary = "Set opening stock for a catalogue item at a location")
    public ResponseEntity<ApiResponse<ScInventory>> openingStock(
            @Valid @RequestBody OpeningStockRequest req) {
        // FIX H-1: getCurrentUserName() — was userId.toString()
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Opening stock set",
                scmService.openingStock(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(),
                        TenantContext.getCurrentUserName(),
                        req)));
    }

    @GetMapping("/inventory/{inventoryId}/movements")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Stock movement history for an inventory record")
    public ResponseEntity<ApiResponse<List<ScStockMovement>>> getMovements(
            @PathVariable UUID inventoryId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getMovements(TenantContext.getTenantIdAsObject(), inventoryId, pageable)));
    }

    // ── Purchase Orders ───────────────────────────────────────────────────────

    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "List purchase orders — optionally filter by status")
    public ResponseEntity<ApiResponse<Page<ScPurchaseOrder>>> getPurchaseOrders(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getPurchaseOrders(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/purchase-orders/{id}")
    @PreAuthorize("hasAuthority('SCM_READ')")
    public ResponseEntity<ApiResponse<ScPurchaseOrder>> getPurchaseOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getPurchaseOrder(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('SCM_ORDER')")
    @Operation(summary = "Create a purchase order — auto-assigns PO number")
    public ResponseEntity<ApiResponse<ScPurchaseOrder>> createPurchaseOrder(
            @Valid @RequestBody CreatePurchaseOrderRequest req) {
        // FIX H-1: getCurrentUserName() — was userId.toString()
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Purchase order created",
                scmService.createPurchaseOrder(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(),
                        TenantContext.getCurrentUserName(),
                        req)));
    }

    @PostMapping("/purchase-orders/{id}/submit")
    @PreAuthorize("hasAuthority('SCM_ORDER')")
    @Operation(summary = "Submit for approval (DRAFT → PENDING_APPROVAL)")
    public ResponseEntity<ApiResponse<ScPurchaseOrder>> submitForApproval(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Submitted for approval",
                scmService.submitForApproval(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/purchase-orders/{id}/approve")
    @PreAuthorize("hasAuthority('SCM_ADMIN')")
    @Operation(summary = "Approve a purchase order (PENDING_APPROVAL → APPROVED)")
    public ResponseEntity<ApiResponse<ScPurchaseOrder>> approvePurchaseOrder(@PathVariable UUID id) {
        // FIX H-1: getCurrentUserName() — was userId.toString()
        // Parameter order matches ScmService: (tenantId, poId, approverId, approverName)
        return ResponseEntity.ok(ApiResponse.success("Purchase order approved",
                scmService.approvePurchaseOrder(TenantContext.getTenantIdAsObject(),
                        id,
                        TenantContext.getCurrentUserId(),
                        TenantContext.getCurrentUserName())));
    }

    @PostMapping("/purchase-orders/{id}/reject")
    @PreAuthorize("hasAuthority('SCM_ADMIN')")
    @Operation(summary = "Reject a PO — returns to DRAFT with reason")
    public ResponseEntity<ApiResponse<ScPurchaseOrder>> rejectPurchaseOrder(
            @PathVariable UUID id, @Valid @RequestBody RejectPoRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Purchase order rejected",
                scmService.rejectPurchaseOrder(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }

    @PostMapping("/purchase-orders/{id}/send")
    @PreAuthorize("hasAuthority('SCM_ORDER')")
    @Operation(summary = "Mark PO as sent to supplier (APPROVED → SENT)")
    public ResponseEntity<ApiResponse<ScPurchaseOrder>> markSent(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Marked as sent",
                scmService.markSent(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/purchase-orders/{id}/lines")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Get line items for a purchase order")
    public ResponseEntity<ApiResponse<List<ScPoLine>>> getPurchaseOrderLines(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getPurchaseOrderLines(TenantContext.getTenantIdAsObject(), id)));
    }

    // NEW: flagged in the gap analysis as "the single most common missing
    // artifact" for a procurement system. Matches Contracting's own
    // download-filename convention (attachment; filename="{id}.pdf").
    @GetMapping(value = "/purchase-orders/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Download a formal Purchase Order PDF")
    public ResponseEntity<byte[]> downloadPoPdf(@PathVariable UUID id) {
        byte[] pdf = scmService.generatePoPdf(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // NEW: backs the goods-receipt picker in the Record Invoice form —
    // previously that field existed in form state but had no input to
    // set it from, meaning the 3-way match's GR-posted check could never
    // actually be exercised from the UI. Scoped to one PO at a time.
    @GetMapping("/purchase-orders/{id}/goods-receipts")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Goods receipts recorded against a specific purchase order — used to link a GR when recording a supplier invoice")
    public ResponseEntity<ApiResponse<List<ScGoodsReceipt>>> getGoodsReceiptsForPo(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getGoodsReceiptsForPurchaseOrder(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/purchase-orders/{id}/lines")
    @PreAuthorize("hasAuthority('SCM_ORDER')")
    @Operation(summary = "Add a line item to a draft purchase order")
    public ResponseEntity<ApiResponse<ScPurchaseOrder>> addPurchaseOrderLine(
            @PathVariable UUID id, @Valid @RequestBody AddPoLineRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Line added",
                scmService.addPurchaseOrderLine(TenantContext.getTenantIdAsObject(), id, req)));
    }

    // ── Goods Receipts ────────────────────────────────────────────────────────

    @GetMapping("/goods-receipts")
    @PreAuthorize("hasAuthority('SCM_READ')")
    public ResponseEntity<ApiResponse<Page<ScGoodsReceipt>>> getGoodsReceipts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getGoodsReceipts(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping("/goods-receipts")
    @PreAuthorize("hasAuthority('SCM_RECEIVE')")
    @Operation(summary = "Create a goods receipt against an approved PO")
    public ResponseEntity<ApiResponse<ScGoodsReceipt>> createGoodsReceipt(
            @Valid @RequestBody CreateGoodsReceiptRequest req) {
        // FIX H-1: getCurrentUserName() — was userId.toString()
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Goods receipt created",
                scmService.createGoodsReceipt(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(),
                        TenantContext.getCurrentUserName(),
                        req)));
    }

    @PostMapping("/goods-receipts/{id}/post")
    @PreAuthorize("hasAuthority('SCM_RECEIVE')")
    @Operation(summary = "Post goods receipt — creates stock movements, updates inventory, transitions PO status")
    public ResponseEntity<ApiResponse<ScGoodsReceipt>> postGoodsReceipt(
            @PathVariable UUID id, @Valid @RequestBody List<PostGrLineRequest> lines) {
        // FIX H-1: getCurrentUserName() — was userId.toString()
        return ResponseEntity.ok(ApiResponse.success("Goods receipt posted — stock updated",
                scmService.postGoodsReceipt(TenantContext.getTenantIdAsObject(),
                        id,
                        TenantContext.getCurrentUserId(),
                        TenantContext.getCurrentUserName(),
                        lines)));
    }

    // NEW: flagged in the gap analysis "for warehouse/delivery sign-off
    // and dispute evidence" — no PDF export existed anywhere for GRNs.
    @GetMapping(value = "/goods-receipts/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Download a Goods Received Note PDF")
    public ResponseEntity<byte[]> downloadGrnPdf(@PathVariable UUID id) {
        byte[] pdf = scmService.generateGrnPdf(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── Supplier Invoices ─────────────────────────────────────────────────────

    @GetMapping("/supplier-invoices")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "List supplier invoices — optionally filter by status")
    public ResponseEntity<ApiResponse<Page<ScSupplierInvoice>>> getSupplierInvoices(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getSupplierInvoices(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @PostMapping("/supplier-invoices")
    @PreAuthorize("hasAuthority('SCM_INVOICE')")
    @Operation(summary = "Record a supplier invoice — performs real 3-way match if PO and GR provided")
    public ResponseEntity<ApiResponse<ScSupplierInvoice>> createSupplierInvoice(
            @Valid @RequestBody CreateSupplierInvoiceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Supplier invoice created",
                scmService.createSupplierInvoice(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/supplier-invoices/{id}/approve")
    @PreAuthorize("hasAuthority('SCM_INVOICE')")
    @Operation(summary = "Approve a supplier invoice (RECEIVED → APPROVED)")
    public ResponseEntity<ApiResponse<ScSupplierInvoice>> approveSupplierInvoice(@PathVariable UUID id) {
        // FIX H-1: getCurrentUserName()
        return ResponseEntity.ok(ApiResponse.success("Invoice approved",
                scmService.approveSupplierInvoice(TenantContext.getTenantIdAsObject(),
                        id,
                        TenantContext.getCurrentUserId(),
                        TenantContext.getCurrentUserName())));
    }

    @PostMapping("/supplier-invoices/{id}/pay")
    @PreAuthorize("hasAuthority('SCM_INVOICE')")
    @Operation(summary = "Mark supplier invoice as paid (APPROVED → PAID)")
    public ResponseEntity<ApiResponse<ScSupplierInvoice>> markPaid(
            @PathVariable UUID id, @Valid @RequestBody MarkPaidRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Invoice marked as paid",
                scmService.markPaid(TenantContext.getTenantIdAsObject(), id, req.paymentReference())));
    }

    // NEW (Tier 1 gap analysis): previously a DISPUTED invoice had no
    // resolution path at all, front or back end. SCM_ADMIN (not
    // SCM_INVOICE) since this is a manager-level override of a flagged
    // discrepancy, matching PO approval's own permission level.
    @PostMapping("/supplier-invoices/{id}/override-dispute")
    @PreAuthorize("hasAuthority('SCM_ADMIN')")
    @Operation(summary = "Override a disputed invoice and approve it anyway — records the override reason for audit")
    public ResponseEntity<ApiResponse<ScSupplierInvoice>> overrideDispute(
            @PathVariable UUID id, @Valid @RequestBody OverrideDisputeRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Dispute overridden — invoice approved",
                scmService.overrideDisputedInvoice(TenantContext.getTenantIdAsObject(), id,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), req.reason())));
    }

    // NEW (Tier 1 gap analysis): the other real resolution for a disputed
    // invoice — reject it instead of overriding it. SCM_INVOICE, same
    // level as approve/pay, since rejecting isn't a mismatch override.
    @PostMapping("/supplier-invoices/{id}/cancel")
    @PreAuthorize("hasAuthority('SCM_INVOICE')")
    @Operation(summary = "Cancel a supplier invoice")
    public ResponseEntity<ApiResponse<ScSupplierInvoice>> cancelInvoice(
            @PathVariable UUID id, @Valid @RequestBody CancelSupplierInvoiceRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Invoice cancelled",
                scmService.cancelSupplierInvoice(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }

    // ── Supplier invoice attachments ────────────────────────────────────────
    // NEW: gap-analysis item — no way to attach a supplier's actual
    // invoice PDF/photo or delivery note for the AP audit trail. Base64-
    // in-DB, following Creative's own proven working pattern — see
    // ScSupplierInvoiceAttachment's class Javadoc for the full reasoning
    // and the two real gaps in Creative's version fixed here.

    @GetMapping("/supplier-invoices/{id}/attachments")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "List attachments on a supplier invoice — metadata only, no file content")
    public ResponseEntity<ApiResponse<List<AttachmentResponse>>> getInvoiceAttachments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getInvoiceAttachments(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/supplier-invoices/{id}/attachments")
    @PreAuthorize("hasAuthority('SCM_INVOICE')")
    @Operation(summary = "Upload an attachment to a supplier invoice (max 10MB)")
    public ResponseEntity<ApiResponse<AttachmentResponse>> uploadInvoiceAttachment(
            @PathVariable UUID id, @Valid @RequestBody UploadAttachmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Attachment uploaded",
                scmService.uploadInvoiceAttachment(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/supplier-invoices/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Download a supplier invoice attachment")
    public ResponseEntity<byte[]> downloadInvoiceAttachment(
            @PathVariable UUID id, @PathVariable UUID attachmentId) {
        var file = scmService.downloadInvoiceAttachment(TenantContext.getTenantIdAsObject(), id, attachmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @DeleteMapping("/supplier-invoices/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('SCM_INVOICE')")
    @Operation(summary = "Delete a supplier invoice attachment")
    public ResponseEntity<ApiResponse<Void>> deleteInvoiceAttachment(
            @PathVariable UUID id, @PathVariable UUID attachmentId) {
        scmService.deleteInvoiceAttachment(TenantContext.getTenantIdAsObject(), id, attachmentId);
        return ResponseEntity.ok(ApiResponse.success("Attachment deleted", null));
    }

    // NEW: flagged in the gap analysis — "remittance advice to the
    // supplier when an invoice is marked paid" was missing entirely.
    // Only available once the invoice is actually PAID — see
    // ScmService.generateRemittanceAdvicePdf()'s own guard.
    @GetMapping(value = "/supplier-invoices/{id}/remittance-advice", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Download a remittance advice PDF for a paid supplier invoice")
    public ResponseEntity<byte[]> downloadRemittanceAdvice(@PathVariable UUID id) {
        byte[] pdf = scmService.generateRemittanceAdvicePdf(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}