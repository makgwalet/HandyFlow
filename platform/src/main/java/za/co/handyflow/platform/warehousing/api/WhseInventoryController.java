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
            @Valid @RequestBody AdjustInventoryRequest req) {
        featureGuard.requireModule("warehousing");
        WhseInventory inv = inventoryService.adjust(TenantContext.getTenantIdAsObject(), id, req.delta(),
                req.reason(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Stock adjusted", toResponse(inv)));
    }

    private InventoryResponse toResponse(WhseInventory i) {
        return new InventoryResponse(i.getId(), i.getClientId(), i.getItemId(), i.getLocationId(), i.getQtyOnHand(),
                i.getQtyAllocated(), i.available());
    }
}
