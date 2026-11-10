package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseInventoryService;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.dto.AdjustInventoryRequest;
import za.co.handyflow.platform.warehousing.dto.ChangeStockStatusRequest;
import za.co.handyflow.platform.warehousing.dto.InventoryResponse;

import java.util.List;
import java.util.UUID;

/** Live stock position reads + manual adjustment. All other mutation happens through the inbound/outbound workflow controllers. */
@RestController
@RequestMapping("/api/v1/warehousing")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Inventory", description = "Live client stock positions")
public class WhseInventoryController {

    private final WhseInventoryService inventoryService;
    private final za.co.handyflow.platform.warehousing.application.internal.WhseAdjustmentApprovalService adjustmentApproval;
    private final za.co.handyflow.platform.warehousing.application.internal.WhseLotAllocationService lotAllocation;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/inventory")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> listForClient(@PathVariable UUID clientId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.listForClient(TenantContext.getTenantIdAsObject(), clientId)
                        .stream().map(this::toResponse).toList()));
    }

    @GetMapping("/inventory/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<InventoryResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(toResponse(inventoryService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/inventory/{id}/adjust")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Manually adjust a stock position (count correction, damage write-off, ...) — always recorded as an ADJUSTMENT movement")
    public ResponseEntity<ApiResponse<InventoryResponse>> adjust(@PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AdjustInventoryRequest req) {
        featureGuard.requireModule("warehousing");
        adjustmentApproval.requireDirectAllowed(TenantContext.getTenantIdAsObject(), req.delta());
        WhseInventory inv = inventoryService.adjust(TenantContext.getTenantIdAsObject(), id, req.delta(),
                req.reason(), TenantContext.getCurrentUserId(), idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Stock adjusted", toResponse(inv)));
    }

    @PostMapping("/clients/{clientId}/inventory/status-change")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Move unallocated stock between statuses (quarantine, release, damaged) — posted as two ledger movements")
    public ResponseEntity<ApiResponse<InventoryResponse>> changeStatus(@PathVariable UUID clientId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ChangeStockStatusRequest req) {
        featureGuard.requireModule("warehousing");
        boolean tracked = lotAllocation.isLotTracked(TenantContext.getTenantIdAsObject().getValue(), req.itemId());
        if (tracked && req.lotId() == null) {
            throw new IllegalArgumentException("This item is lot tracked: choose the lot whose status is changing.");
        }
        WhseInventory dest = inventoryService.changeStatus(TenantContext.getTenantIdAsObject(), clientId, req.itemId(),
                req.locationId(), tracked ? req.lotId() : null, req.fromStatus(), req.toStatus(), req.qty(), req.reason(),
                TenantContext.getCurrentUserId(), idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Stock status changed", toResponse(dest)));
    }

    private InventoryResponse toResponse(WhseInventory i) {
        return new InventoryResponse(i.getId(), i.getClientId(), i.getItemId(), i.getLocationId(), i.getQtyOnHand(),
                i.getQtyAllocated(), i.available(), i.getStatus(), i.getLotId());
    }
}
