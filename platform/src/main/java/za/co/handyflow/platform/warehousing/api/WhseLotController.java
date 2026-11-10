package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseLotService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Lots and expiry", description = "Batch/lot tracking and expiry")
public class WhseLotController {

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";

    private final WhseLotService service;
    private final FeatureGuard featureGuard;

    public record ReasonRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(min = 5, max = 300) String reason) {}
    public record TrackingRequest(boolean lotTracked, boolean expiryTracked, @Min(0) Integer minShelfLifeDays) {}

    @GetMapping("/lots")
    @PreAuthorize(READ)
    @Operation(summary = "Lots with their stock and days to expiry, filterable by client and item")
    public ResponseEntity<ApiResponse<List<WhseLotService.LotRow>>> lots(@RequestParam(required = false) UUID clientId, @RequestParam(required = false) UUID itemId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), clientId, itemId)));
    }

    @GetMapping("/lots/expiry-report")
    @PreAuthorize(READ)
    @Operation(summary = "Stock that is expired or expires within the given days (default 30, at most 730), soonest first")
    public ResponseEntity<ApiResponse<List<WhseLotService.ExpiryRow>>> expiry(@RequestParam(required = false) UUID clientId, @RequestParam(defaultValue = "30") int days) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.expiryReport(TenantContext.getTenantIdAsObject(), clientId, days)));
    }

    @PostMapping("/lots/{id}/block")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Block a lot (recall or quality hold). Returns the open orders that hold stock of it and need re-allocating.")
    public ResponseEntity<ApiResponse<List<WhseLotService.AffectedOrder>>> block(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Lot blocked", service.block(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/lots/{id}/unblock")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> unblock(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("warehousing");
        service.unblock(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Lot released", null));
    }

    @PutMapping("/items/{id}/tracking")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Switch lot and expiry tracking for an item. Refused while stock is on hand.")
    public ResponseEntity<ApiResponse<WhseLotService.Tracking>> tracking(@PathVariable UUID id, @Valid @RequestBody TrackingRequest req) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Tracking saved", service.setTracking(TenantContext.getTenantIdAsObject(), id,
                req.lotTracked(), req.expiryTracked(), req.minShelfLifeDays(), TenantContext.getCurrentUserId())));
    }
}
