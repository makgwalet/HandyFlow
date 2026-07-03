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
import za.co.handyflow.platform.shared.UserContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving/assets")
@RequiredArgsConstructor
@Tag(name = "Earthmoving - Assets", description = "Heavy equipment lifecycle management")
public class EarthAssetController {

    private final EarthAssetService earthAssetService;
    private final FeatureGuard featureGuard;

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
    public ResponseEntity<ApiResponse<AssetResponse>> createAsset(@Valid @RequestBody CreateAssetRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Asset registered",
                        earthAssetService.createAsset(TenantContext.getTenantIdAsObject(), request)));
    }

    // FIX: reverted to @PatchMapping, which is the semantically correct verb
    // for "change one field (status) on an existing resource" — @PutMapping
    // implies replacing the entire resource representation.
    //
    // The original comment on this endpoint said PUT was used because PATCH
    // triggers a CORS preflight that Spring's default config doesn't allow.
    // That's treating the symptom, not the cause: EVERY non-simple request
    // (including this PUT once it carries a JSON body and custom headers)
    // already triggers a preflight in most real frontend setups; swapping
    // the verb doesn't avoid preflights, it just relabels the endpoint
    // incorrectly. The actual fix is to allow PATCH in your CORS config:
    //
    //   CorsConfiguration config = new CorsConfiguration();
    //   config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    //   // ... register on your CorsConfigurationSource bean
    //
    // Find wherever your app defines its CorsConfigurationSource (likely a
    // SecurityConfig or WebMvcConfigurer) and make sure PATCH is in
    // allowedMethods. That fixes CORS for every PATCH endpoint at once,
    // instead of every controller quietly avoiding a valid HTTP verb.
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Update asset status: AVAILABLE, DEPLOYED, MAINTENANCE, BREAKDOWN, HIRED_OUT, RETIRED")
    public ResponseEntity<ApiResponse<AssetResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetStatusRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                earthAssetService.updateStatus(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/{id}/hours")
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

    // NEW: deployment history was previously invisible — EarthAsset.currentSite/
    // currentClient only ever held the latest value, overwritten on every
    // redeployment. See EarthDeployment's Javadoc for the full rationale.
    @GetMapping("/{id}/deployments")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Deployment history for an asset — every site it's been sent to, with contact and date details")
    public ResponseEntity<ApiResponse<Page<DeploymentResponse>>> getDeploymentHistory(
            @PathVariable UUID id,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(
                earthAssetService.getDeploymentHistory(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        // FIX: previously always deleted with deletedBy=null, losing the
        // audit trail of who deleted the asset. See UserContext for the
        // one place this needs to be wired to your actual auth principal.
        earthAssetService.deleteAsset(TenantContext.getTenantIdAsObject(), id, UserContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Asset deleted", null));
    }

    // ── Maintenance ───────────────────────────────────────────────────────

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

    // ── Operator Logs ─────────────────────────────────────────────────────

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

    // NEW: OperatorLog.complete() existed on the entity with no way to reach
    // it via the API — a shift could be started but never ended.
    @PatchMapping("/{id}/operator-logs/{logId}/complete")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Complete an operator's shift — records end time, end hours and fuel used")
    public ResponseEntity<ApiResponse<OperatorLogResponse>> completeLog(
            @PathVariable UUID id,
            @PathVariable UUID logId,
            @Valid @RequestBody CompleteOperatorLogRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Operator log completed",
                earthAssetService.completeOperatorLog(TenantContext.getTenantIdAsObject(), id, logId, request)));
    }
}