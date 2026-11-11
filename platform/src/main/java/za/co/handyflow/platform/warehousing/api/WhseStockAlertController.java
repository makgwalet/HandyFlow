package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseStockAlertService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Stock alerts per warehouse, with warehouse-specific minimum and maximum levels that override the item's defaults. */
@RestController
@RequestMapping("/api/v1/warehousing/stock-alerts")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Stock alerts", description = "Threshold alerts per warehouse, with acknowledge and resolve")
public class WhseStockAlertController {

    private final WhseStockAlertService service;
    private final FeatureGuard featureGuard;

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";

    public record ResolveRequest(String note) {}
    public record ThresholdRequest(UUID warehouseId, UUID itemId, BigDecimal minQty, BigDecimal maxQty) {}

    /** status: omit for open and acknowledged alerts, RESOLVED for the closed ones. kind: OUT_OF_STOCK, BELOW_MIN or ABOVE_MAX. */
    @GetMapping
    @PreAuthorize(READ)
    @Operation(summary = "Stock alerts, brought up to date with the stock")
    public ResponseEntity<ApiResponse<List<WhseStockAlertService.Alert>>> list(@RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String status, @RequestParam(required = false) String kind) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), warehouseId, clientId, status, kind)));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> acknowledge(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        service.acknowledge(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Acknowledged", null));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> resolve(@PathVariable UUID id, @RequestBody ResolveRequest r) {
        featureGuard.requireModule("warehousing");
        service.resolve(TenantContext.getTenantIdAsObject(), id, r.note(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Resolved", null));
    }

    @GetMapping("/thresholds")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WhseStockAlertService.Threshold>>> thresholds(@RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) UUID itemId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.thresholds(TenantContext.getTenantIdAsObject(), warehouseId, itemId)));
    }

    @PutMapping("/thresholds")
    @PreAuthorize(WRITE)
    @Operation(summary = "Set a warehouse's own minimum and/or maximum for an item; a blank level falls back to the item's default")
    public ResponseEntity<ApiResponse<Void>> setThreshold(@RequestBody ThresholdRequest r) {
        featureGuard.requireModule("warehousing");
        service.setThreshold(TenantContext.getTenantIdAsObject(), r.warehouseId(), r.itemId(), r.minQty(), r.maxQty(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Saved", null));
    }

    @DeleteMapping("/thresholds")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> clearThreshold(@RequestParam UUID warehouseId, @RequestParam UUID itemId) {
        featureGuard.requireModule("warehousing");
        service.clearThreshold(TenantContext.getTenantIdAsObject(), warehouseId, itemId, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Cleared", null));
    }
}
