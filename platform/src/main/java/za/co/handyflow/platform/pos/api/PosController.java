package za.co.handyflow.platform.pos.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.pos.application.internal.PosService;
import za.co.handyflow.platform.pos.domain.model.PosStockMovement;
import za.co.handyflow.platform.pos.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pos")
@RequiredArgsConstructor
@Tag(name = "POS & Stock", description = "Point of sale, inventory management and purchase orders")
public class PosController {

    private final PosService posService;

    // ── Summary ───────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "POS dashboard — sales today, this month, low stock alerts, pending POs")
    public ResponseEntity<ApiResponse<PosSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getSummary(TenantContext.getTenantIdAsObject())));
    }

    // ── Cash Sessions ─────────────────────────────────────────────────────────

    @GetMapping("/cash-sessions/current")
    @PreAuthorize("hasAuthority('POS_SELL')")
    @Operation(summary = "Get the currently open cash session, if any")
    public ResponseEntity<ApiResponse<CashSessionResponse>> getCurrentSession() {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getOpenSession(TenantContext.getTenantIdAsObject()).orElse(null)));
    }

    @GetMapping("/cash-sessions")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "List all cash sessions — history of shifts")
    public ResponseEntity<ApiResponse<Page<CashSessionResponse>>> getSessions(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getSessions(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping("/cash-sessions/open")
    @PreAuthorize("hasAuthority('POS_SELL')")
    @Operation(summary = "Open a cash session — must be done before processing CASH sales")
    public ResponseEntity<ApiResponse<CashSessionResponse>> openSession(
            @Valid @RequestBody OpenCashSessionRequest req) {
        String userName = TenantContext.getCurrentUserName();
        return ResponseEntity.status(201).body(ApiResponse.success("Cash session opened",
                posService.openCashSession(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), userName, req)));
    }

    @PostMapping("/cash-sessions/{id}/close")
    @PreAuthorize("hasAuthority('POS_SELL')")
    @Operation(summary = "Close a cash session — triggers cash-up and variance calculation")
    public ResponseEntity<ApiResponse<CashSessionResponse>> closeSession(
            @PathVariable UUID id,
            @Valid @RequestBody CloseCashSessionRequest req) {
        String userName = TenantContext.getCurrentUserName();
        return ResponseEntity.ok(ApiResponse.success("Cash session closed",
                posService.closeCashSession(TenantContext.getTenantIdAsObject(), id,
                        TenantContext.getCurrentUserId(), userName, req)));
    }

    @GetMapping("/cash-sessions/{id}/z-report")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "Generate Z-report for a session — end-of-day sales summary")
    public ResponseEntity<ApiResponse<ZReportResponse>> getZReport(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getZReport(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── POS Terminal — Sales ──────────────────────────────────────────────────

    @PostMapping("/sell")
    @PreAuthorize("hasAuthority('POS_SELL')")
    @Operation(summary = "Process a sale — validates stock, deducts inventory, calculates change. " +
            "Supports single payment, split payment (CASH+CARD), and transaction-level discounts.")
    public ResponseEntity<ApiResponse<TransactionResponse>> processSale(
            @Valid @RequestBody ProcessSaleRequest req) {
        String staffName = TenantContext.getCurrentUserName();
        return ResponseEntity.status(201).body(ApiResponse.success("Sale completed",
                posService.processSale(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), staffName, req)));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "List sales transactions — excludes VOIDED by default")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactions(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getTransactions(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "Get full transaction detail including all line items")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getTransaction(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/transactions/{id}/receipt")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "Get structured receipt data including pre-rendered HTML — for printing or emailing")
    public ResponseEntity<ApiResponse<ReceiptResponse>> getReceipt(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getReceipt(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/transactions/{id}/void")
    @PreAuthorize("hasAuthority('POS_ADMIN')")
    @Operation(summary = "Void a transaction — reverses all stock movements. Requires POS_ADMIN.")
    public ResponseEntity<ApiResponse<TransactionResponse>> voidTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody VoidTransactionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Transaction voided",
                posService.voidTransaction(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/transactions/{id}/refund")
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    @Operation(summary = "Process a partial or full refund against a completed transaction. " +
            "Restores stock and creates a linked REFUND transaction record.")
    public ResponseEntity<ApiResponse<TransactionResponse>> refundTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody ProcessRefundRequest req) {
        String staffName = TenantContext.getCurrentUserName();
        return ResponseEntity.status(201).body(ApiResponse.success("Refund processed",
                posService.processRefund(TenantContext.getTenantIdAsObject(), id,
                        TenantContext.getCurrentUserId(), staffName, req)));
    }

    // ── Barcode lookup ────────────────────────────────────────────────────────

    @GetMapping("/barcode/{barcode}")
    @PreAuthorize("hasAuthority('POS_SELL')")
    @Operation(summary = "Look up a stock item by barcode — for POS terminal scanning")
    public ResponseEntity<ApiResponse<StockItemResponse>> lookupBarcode(
            @PathVariable String barcode) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.lookupByBarcode(TenantContext.getTenantIdAsObject(), barcode)));
    }

    // ── Stock Items ───────────────────────────────────────────────────────────

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "List all stock items with current quantities, selling prices and low-stock flags")
    public ResponseEntity<ApiResponse<Page<StockItemResponse>>> getStockItems(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getStockItems(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/stock/low")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "Items below reorder level — use this to drive purchase order creation")
    public ResponseEntity<ApiResponse<List<StockItemResponse>>> getLowStock() {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getLowStockItems(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/stock")
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    @Operation(summary = "Add a catalogue item to stock tracking — creates opening stock movement if qty > 0")
    public ResponseEntity<ApiResponse<StockItemResponse>> createStockItem(
            @Valid @RequestBody CreateStockItemRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Stock item created",
                posService.createStockItem(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/stock/{id}")
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    @Operation(summary = "Update reorder level, cost price or bin location for a stock item")
    public ResponseEntity<ApiResponse<StockItemResponse>> updateStockItem(
            @PathVariable UUID id,
            @RequestBody UpdateStockItemRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Stock item updated",
                posService.updateStockItem(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @GetMapping("/stock/{id}/movements")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "Full audit trail of every stock in/out for an item — SALE, PURCHASE, ADJUSTMENT, RETURN, WASTE")
    public ResponseEntity<ApiResponse<List<PosStockMovement>>> getMovements(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getStockMovements(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Stock Adjustments ─────────────────────────────────────────────────────

    @GetMapping("/adjustments")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "List stock adjustments — stocktakes, damage write-offs, theft")
    public ResponseEntity<ApiResponse<Page<StockAdjustmentResponse>>> getAdjustments(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getAdjustments(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    @Operation(summary = "Create a stock adjustment in DRAFT — add lines, review, then apply")
    public ResponseEntity<ApiResponse<StockAdjustmentResponse>> createAdjustment(
            @Valid @RequestBody CreateStockAdjustmentRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Adjustment created",
                posService.createAdjustment(TenantContext.getTenantIdAsObject(), req,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/adjustments/{id}/apply")
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    @Operation(summary = "Apply a DRAFT adjustment — permanently updates stock levels and creates movement records")
    public ResponseEntity<ApiResponse<StockAdjustmentResponse>> applyAdjustment(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Adjustment applied",
                posService.applyAdjustment(TenantContext.getTenantIdAsObject(), id,
                        TenantContext.getCurrentUserId())));
    }

    // ── Purchase Orders ───────────────────────────────────────────────────────

    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "List purchase orders — filter by status using ?status=ORDERED")
    public ResponseEntity<ApiResponse<Page<PurchaseOrderResponse>>> getPurchaseOrders(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getPurchaseOrders(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    @Operation(summary = "Create a purchase order — automatically sets status to ORDERED")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createPurchaseOrder(
            @Valid @RequestBody CreatePurchaseOrderRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Purchase order created",
                posService.createPurchaseOrder(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PostMapping("/purchase-orders/{id}/receive")
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    @Operation(summary = "Record delivery against a PO — adds to stock, updates cost price, marks RECEIVED or PARTIALLY_RECEIVED")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> receiveStock(
            @PathVariable UUID id,
            @RequestBody ReceiveStockRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Stock received",
                posService.receiveStock(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }
}
