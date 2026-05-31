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
    @Operation(summary = "POS dashboard — sales today, this month, low stock alerts")
    public ResponseEntity<ApiResponse<PosSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getSummary(TenantContext.getTenantIdAsObject())));
    }

    // ── POS Terminal ──────────────────────────────────────────────────────────

    @PostMapping("/sell")
    @PreAuthorize("hasAuthority('POS_SELL')")
    @Operation(summary = "Process a sale — deducts stock, posts accounting journal, calculates change")
    public ResponseEntity<ApiResponse<TransactionResponse>> processSale(
            @Valid @RequestBody ProcessSaleRequest req) {
        String staffName = "Cashier"; // TODO: wire to user profile
        return ResponseEntity.status(201).body(ApiResponse.success("Sale completed",
                posService.processSale(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), staffName, req)));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "List sales transactions")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactions(
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getTransactions(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "Get transaction detail with line items")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getTransaction(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/transactions/{id}/void")
    @PreAuthorize("hasAuthority('POS_ADMIN')")
    @Operation(summary = "Void a transaction — reverses stock movements")
    public ResponseEntity<ApiResponse<TransactionResponse>> voidTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody VoidTransactionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Transaction voided",
                posService.voidTransaction(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
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

    // ── Stock items ───────────────────────────────────────────────────────────

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "List all stock items with current quantities")
    public ResponseEntity<ApiResponse<Page<StockItemResponse>>> getStockItems(
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getStockItems(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/stock/low")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "Get items below reorder level — low stock alert list")
    public ResponseEntity<ApiResponse<List<StockItemResponse>>> getLowStock() {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getLowStockItems(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/stock")
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    @Operation(summary = "Add a catalogue item to stock tracking")
    public ResponseEntity<ApiResponse<StockItemResponse>> createStockItem(
            @Valid @RequestBody CreateStockItemRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Stock item created",
                posService.createStockItem(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/stock/{id}")
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    @Operation(summary = "Update stock item settings — reorder level, cost price, location")
    public ResponseEntity<ApiResponse<StockItemResponse>> updateStockItem(
            @PathVariable UUID id,
            @RequestBody UpdateStockItemRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Stock item updated",
                posService.updateStockItem(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @GetMapping("/stock/{id}/movements")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "Get stock movement history for an item — full audit trail")
    public ResponseEntity<ApiResponse<List<PosStockMovement>>> getMovements(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getStockMovements(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Purchase orders ───────────────────────────────────────────────────────

    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('POS_READ')")
    @Operation(summary = "List purchase orders")
    public ResponseEntity<ApiResponse<Page<PurchaseOrderResponse>>> getPurchaseOrders(
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                posService.getPurchaseOrders(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping("/purchase-orders")
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    @Operation(summary = "Create a purchase order to restock from supplier")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createPurchaseOrder(
            @Valid @RequestBody CreatePurchaseOrderRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Purchase order created",
                posService.createPurchaseOrder(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PostMapping("/purchase-orders/{id}/receive")
    @PreAuthorize("hasAuthority('POS_MANAGE')")
    @Operation(summary = "Record stock received against a purchase order — adds to inventory")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> receiveStock(
            @PathVariable UUID id,
            @RequestBody ReceiveStockRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Stock received",
                posService.receiveStock(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }
}
