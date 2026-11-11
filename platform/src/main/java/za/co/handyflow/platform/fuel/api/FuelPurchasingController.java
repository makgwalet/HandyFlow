package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelPurchasingService;
import za.co.handyflow.platform.fuel.dto.CancelRequest;
import za.co.handyflow.platform.fuel.dto.PurchaseOrderMatchResponse;
import za.co.handyflow.platform.fuel.dto.PurchaseOrderRequest;
import za.co.handyflow.platform.fuel.dto.PurchaseOrderResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Stage 4C: supplier purchase orders. */
@RestController
@RequestMapping("/api/v1/fuel/purchase-orders")
@RequiredArgsConstructor
@Tag(name = "Fuel Purchase Orders", description = "Orders placed with fuel suppliers, matched to receipts")
public class FuelPurchasingController {

    private final FuelPurchasingService purchasing;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<PurchaseOrderResponse>>> list() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(purchasing.list(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/{id}/match")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<PurchaseOrderMatchResponse>> match(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(purchasing.match(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> create(@Valid @RequestBody PurchaseOrderRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Created",
                purchasing.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> update(@PathVariable UUID id, @Valid @RequestBody PurchaseOrderRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Updated", purchasing.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/place")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> place(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Placed", purchasing.place(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Cancelled", purchasing.cancel(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }

    @PostMapping("/{id}/close-short")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> closeShort(@PathVariable UUID id, @Valid @RequestBody CancelRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Closed", purchasing.closeShort(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        purchasing.remove(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Removed", null));
    }
}
