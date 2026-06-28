package za.co.handyflow.platform.supplychain.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.supplychain.application.internal.ScmService;
import za.co.handyflow.platform.supplychain.domain.model.*;
import za.co.handyflow.platform.supplychain.dto.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/supply-chain")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Suppliers, purchase orders, inventory and AP invoices")
public class ScmController {

    private final ScmService scmService;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "SCM dashboard — supplier count, open POs, pending invoices, low stock alerts")
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
        UUID userId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Supplier created",
                scmService.createSupplier(TenantContext.getTenantIdAsObject(), userId, req)));
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasAuthority('SCM_ADMIN')")
    public ResponseEntity<ApiResponse<ScSupplier>> updateSupplier(
            @PathVariable UUID id, @RequestBody UpdateSupplierRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Supplier updated",
                scmService.updateSupplier(TenantContext.getTenantIdAsObject(), id, req)));
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
            @RequestBody CreateLocationRequest req) {
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
            @RequestBody OpeningStockRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        String userName = userId != null ? userId.toString() : "system";
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Opening stock set",
                scmService.openingStock(TenantContext.getTenantIdAsObject(), userId, userName, req)));
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
            @RequestBody CreatePurchaseOrderRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        String userName = userId != null ? userId.toString() : "system";
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Purchase order created",
                scmService.createPurchaseOrder(TenantContext.getTenantIdAsObject(), userId, userName, req)));
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
        UUID userId = TenantContext.getCurrentUserId();
        String name = userId != null ? userId.toString() : "approver";
        return ResponseEntity.ok(ApiResponse.success("Purchase order approved",
                scmService.approvePurchaseOrder(TenantContext.getTenantIdAsObject(), id, userId, name)));
    }

    @PostMapping("/purchase-orders/{id}/reject")
    @PreAuthorize("hasAuthority('SCM_ADMIN')")
    @Operation(summary = "Reject a PO — returns to DRAFT with reason")
    public ResponseEntity<ApiResponse<ScPurchaseOrder>> rejectPurchaseOrder(
            @PathVariable UUID id, @RequestBody RejectPoRequest req) {
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
            @RequestBody CreateGoodsReceiptRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        String userName = userId != null ? userId.toString() : "system";
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Goods receipt created",
                scmService.createGoodsReceipt(TenantContext.getTenantIdAsObject(), userId, userName, req)));
    }

    @PostMapping("/goods-receipts/{id}/post")
    @PreAuthorize("hasAuthority('SCM_RECEIVE')")
    @Operation(summary = "Post goods receipt — creates stock movements and updates inventory")
    public ResponseEntity<ApiResponse<ScGoodsReceipt>> postGoodsReceipt(
            @PathVariable UUID id, @RequestBody List<PostGrLineRequest> lines) {
        UUID userId = TenantContext.getCurrentUserId();
        String userName = userId != null ? userId.toString() : "system";
        return ResponseEntity.ok(ApiResponse.success("Goods receipt posted — stock updated",
                scmService.postGoodsReceipt(TenantContext.getTenantIdAsObject(), id, userId, userName, lines)));
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
    @Operation(summary = "Record a supplier invoice — auto 3-way match if PO and GR provided")
    public ResponseEntity<ApiResponse<ScSupplierInvoice>> createSupplierInvoice(
            @RequestBody CreateSupplierInvoiceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Supplier invoice created",
                scmService.createSupplierInvoice(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/supplier-invoices/{id}/approve")
    @PreAuthorize("hasAuthority('SCM_INVOICE')")
    @Operation(summary = "Approve a supplier invoice (RECEIVED → APPROVED)")
    public ResponseEntity<ApiResponse<ScSupplierInvoice>> approveSupplierInvoice(@PathVariable UUID id) {
        UUID userId = TenantContext.getCurrentUserId();
        String name = userId != null ? userId.toString() : "approver";
        return ResponseEntity.ok(ApiResponse.success("Invoice approved",
                scmService.approveSupplierInvoice(TenantContext.getTenantIdAsObject(), id, userId, name)));
    }

    @PostMapping("/supplier-invoices/{id}/pay")
    @PreAuthorize("hasAuthority('SCM_INVOICE')")
    @Operation(summary = "Mark supplier invoice as paid (APPROVED → PAID)")
    public ResponseEntity<ApiResponse<ScSupplierInvoice>> markPaid(
            @PathVariable UUID id, @RequestBody MarkPaidRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Invoice marked as paid",
                scmService.markPaid(TenantContext.getTenantIdAsObject(), id, req.paymentReference())));
    }


    /**
     * Add a line item to a DRAFT purchase order.
     * The PO total is recalculated after each line is added.
     */
    @PostMapping("/purchase-orders/{id}/lines")
    @PreAuthorize("hasAuthority('SCM_ORDER')")
    @Operation(summary = "Add a line item to a draft purchase order")
    public ResponseEntity<ApiResponse<ScPurchaseOrder>> addPurchaseOrderLine(
            @PathVariable UUID id,
            @RequestBody AddPoLineRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Line added",
                scmService.addPurchaseOrderLine(TenantContext.getTenantIdAsObject(), id, req)));
    }

    /**
     * GET /purchase-orders/{id}/lines
     * Returns the line items for a purchase order.
     * ScPurchaseOrder is a flat entity — lines live in sc_po_lines.
     */
    @GetMapping("/purchase-orders/{id}/lines")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Get line items for a purchase order")
    public ResponseEntity<ApiResponse<List<ScPoLine>>> getPurchaseOrderLines(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scmService.getPurchaseOrderLines(TenantContext.getTenantIdAsObject(), id)));
    }




}
