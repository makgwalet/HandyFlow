package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.agriculture.application.internal.AgInventoryItemService;
import za.co.handyflow.platform.agriculture.application.internal.AgStockMovementService;
import za.co.handyflow.platform.agriculture.dto.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Farm-scoped feed/seed/fertiliser/chemical/veterinary stock. Receive/
 * issue/adjust each also append a matching {@code AgStockMovement} row —
 * see AgInventoryItemService's own Javadoc.
 */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Inventory", description = "Farm stock items and stock movements")
public class AgInventoryItemController {

    private final AgInventoryItemService inventoryItemService;
    private final AgStockMovementService stockMovementService;
    private final FeatureGuard featureGuard;

    @GetMapping("/farms/{farmId}/inventory-items")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<InventoryItemResponse>>> getItemsForFarm(
            @PathVariable UUID farmId, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                inventoryItemService.getItemsForFarm(TenantContext.getTenantIdAsObject(), farmId, pageable)));
    }

    @PostMapping("/farms/{farmId}/inventory-items")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Add a stock item to a farm")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> createItem(
            @PathVariable UUID farmId, @Valid @RequestBody CreateInventoryItemRequest request) {
        featureGuard.requireModule("agriculture");
        if (!farmId.equals(request.farmId())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("farmId in path and body must match"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Inventory item created",
                inventoryItemService.createItem(TenantContext.getTenantIdAsObject(), request)));
    }

    @GetMapping("/inventory-items/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> getItem(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                inventoryItemService.getItem(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/inventory-items/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> updateItem(
            @PathVariable UUID id, @Valid @RequestBody UpdateInventoryItemRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Inventory item updated",
                inventoryItemService.updateItem(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/inventory-items/{id}/receive")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Record stock received — increases current quantity")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> receive(
            @PathVariable UUID id, @Valid @RequestBody ReceiveInventoryRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Stock received",
                inventoryItemService.receive(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/inventory-items/{id}/issue")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Record stock issued — decreases current quantity, fails if insufficient stock")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> issue(
            @PathVariable UUID id, @Valid @RequestBody IssueInventoryRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Stock issued",
                inventoryItemService.issue(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/inventory-items/{id}/adjust")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Set current quantity directly — e.g. after a stock count")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> adjust(
            @PathVariable UUID id, @Valid @RequestBody AdjustInventoryRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Stock adjusted",
                inventoryItemService.adjust(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/inventory-items/{id}/deactivate")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> deactivateItem(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Inventory item deactivated",
                inventoryItemService.deactivateItem(TenantContext.getTenantIdAsObject(), id)));
    }

    @PatchMapping("/inventory-items/{id}/reactivate")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<InventoryItemResponse>> reactivateItem(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Inventory item reactivated",
                inventoryItemService.reactivateItem(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/inventory-items/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteItem(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        inventoryItemService.deleteItem(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Inventory item deleted", null));
    }

    @GetMapping("/inventory-items/{id}/stock-movements")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<StockMovementResponse>>> getStockMovements(
            @PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                stockMovementService.getMovementsForItem(TenantContext.getTenantIdAsObject(), id, pageable)));
    }
}
