package za.co.handyflow.platform.earthmoving.api;

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
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthAssetService;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving/assets")
@RequiredArgsConstructor
@Tag(name = "Earthmoving - Assets", description = "Heavy equipment lifecycle management")
public class EarthAssetController {

    private final EarthAssetService earthAssetService;
    private final FeatureGuard      featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all assets, optionally filter by status or type")
    public ResponseEntity<ApiResponse<Page<AssetResponse>>> getAssets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assetType,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(
                earthAssetService.getAssets(TenantContext.getTenantIdAsObject(), status, assetType, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<AssetResponse>> getAsset(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(
                earthAssetService.getAsset(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Register a new heavy equipment asset (owned, hired-in or hired-out)")
    public ResponseEntity<ApiResponse<AssetResponse>> createAsset(
            @Valid @RequestBody CreateAssetRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Asset registered",
                        earthAssetService.createAsset(TenantContext.getTenantIdAsObject(), request)));
    }

    // FIX: Changed from @PatchMapping to @PutMapping.
    // PATCH triggers a CORS preflight that Spring's default CORS config
    // does not allow — the browser sends OPTIONS before PATCH and gets
    // no Access-Control-Allow-Origin header back, blocking the request.
    // PUT does not trigger a preflight for simple CORS configs.
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Update asset status: AVAILABLE, DEPLOYED, MAINTENANCE, BREAKDOWN, HIRED_OUT, RETIRED")
    public ResponseEntity<ApiResponse<AssetResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetStatusRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                earthAssetService.updateStatus(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PutMapping("/{id}/hours")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Update the current hour meter reading on an asset")
    public ResponseEntity<ApiResponse<AssetResponse>> updateHours(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHoursRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Hour meter updated",
                earthAssetService.updateHours(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PutMapping("/{id}/deploy")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Deploy asset to a site and client")
    public ResponseEntity<ApiResponse<AssetResponse>> deploy(
            @PathVariable UUID id,
            @RequestBody DeployAssetRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Asset deployed",
                earthAssetService.deploy(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        earthAssetService.deleteAsset(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Asset deleted", null));
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    @GetMapping("/{id}/maintenance")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<MaintenanceResponse>>> getMaintenance(
            @PathVariable UUID id,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(
                earthAssetService.getMaintenanceHistory(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/{id}/maintenance")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Record a maintenance event — updates last service hours if type is SERVICE")
    public ResponseEntity<ApiResponse<MaintenanceResponse>> recordMaintenance(
            @PathVariable UUID id,
            @Valid @RequestBody CreateMaintenanceRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Maintenance recorded",
                        earthAssetService.recordMaintenance(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Operator Logs ─────────────────────────────────────────────────────────

    @GetMapping("/{id}/operator-logs")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<OperatorLogResponse>>> getOperatorLogs(
            @PathVariable UUID id,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(
                earthAssetService.getOperatorLogs(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/{id}/operator-logs")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Log an operator starting a shift on an asset")
    public ResponseEntity<ApiResponse<OperatorLogResponse>> startLog(
            @PathVariable UUID id,
            @Valid @RequestBody CreateOperatorLogRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Operator log started",
                        earthAssetService.startOperatorLog(TenantContext.getTenantIdAsObject(), id, request)));
    }
}
