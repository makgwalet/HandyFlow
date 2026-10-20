package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthTelematicsService;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

/** Where machines were last seen. Provider-neutral: no tracking provider is chosen, so readings come from adapters added later or from people. */
@RestController
@RequestMapping("/api/v1/earthmoving/telematics")
@RequiredArgsConstructor
@Tag(name = "Earthmoving tracking")
public class EarthTelematicsController {

    private final EarthTelematicsService telematics;
    private final FeatureGuard featureGuard;

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "Last position and meter value of every machine, provider and manual readings apart, with how fresh they are")
    public ResponseEntity<ApiResponse<List<TrackingStatusResponse>>> status() {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Tracking status", telematics.status(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/providers")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "Provider adapters installed (empty until a provider is chosen)")
    public ResponseEntity<ApiResponse<List<String>>> providers() {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Providers", telematics.installedProviders()));
    }

    @GetMapping("/devices")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    public ResponseEntity<ApiResponse<List<TelematicsDeviceResponse>>> devices(@RequestParam(defaultValue = "false") boolean includeUnmapped) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Devices", telematics.devices(TenantContext.getTenantIdAsObject(), includeUnmapped)));
    }

    @PostMapping("/devices")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    public ResponseEntity<ApiResponse<TelematicsDeviceResponse>> map(@Valid @RequestBody MapTelematicsDeviceRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Device mapped", telematics.map(TenantContext.getTenantIdAsObject(), request, UserContext.getCurrentUserId())));
    }

    @PostMapping("/devices/{id}/unmap")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    public ResponseEntity<ApiResponse<TelematicsDeviceResponse>> unmap(@PathVariable UUID id, @RequestBody UnmapTelematicsDeviceRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Device unmapped", telematics.unmap(TenantContext.getTenantIdAsObject(), id, request, UserContext.getCurrentUserId())));
    }

    @GetMapping("/assets/{assetId}/readings")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    public ResponseEntity<ApiResponse<List<TelematicsReadingResponse>>> history(@PathVariable UUID assetId, @RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Readings", telematics.history(TenantContext.getTenantIdAsObject(), assetId, limit)));
    }

    @PostMapping("/assets/{assetId}/readings")
    @PreAuthorize("hasAuthority('EARTHMOVING_WRITE')")
    @Operation(summary = "Record a position and/or meter value by hand. Kept apart from provider readings; the machine's own meter is not changed")
    public ResponseEntity<ApiResponse<TelematicsReadingResponse>> record(@PathVariable UUID assetId, @RequestBody ManualTelematicsReadingRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Reading recorded", telematics.recordManual(TenantContext.getTenantIdAsObject(), assetId, request, UserContext.getCurrentUserId())));
    }
}
