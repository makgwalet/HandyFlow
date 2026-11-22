package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.DeviceHeartbeatService;
import za.co.handyflow.platform.security.application.internal.FleetHealthService;
import za.co.handyflow.platform.security.dto.FleetHealthResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Device fleet health")
public class FleetHealthController {

    public record HeartbeatRequest(@NotBlank String deviceHardwareId, Integer batteryPct, String appVersion) {}

    private final FleetHealthService fleetHealth;
    private final DeviceHeartbeatService heartbeat;
    private final FeatureGuard featureGuard;

    @GetMapping("/api/v1/security/devices/fleet-health")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Phones that need attention: silent on shift, never seen, low battery, old app version")
    public ResponseEntity<ApiResponse<FleetHealthResponse>> health() {
        return ResponseEntity.ok(ApiResponse.success("OK", fleetHealth.health(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/api/v1/guard/me/heartbeat")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "The phone reports that it is alive, with battery and app version",
            description = "Always the caller's own phone; a phone that belongs to another guard answers 404. Call about every 5 minutes while the app is open.")
    public ResponseEntity<ApiResponse<Void>> report(@Valid @RequestBody HeartbeatRequest req) {
        featureGuard.requireModule("security");
        heartbeat.report(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(),
                req.deviceHardwareId(), req.batteryPct(), req.appVersion());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
