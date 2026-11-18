package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelPushService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

/** The Fuel app registers its phone here so the server can alert the signed-in person. Always the caller's own token. */
@RestController
@RequestMapping("/api/v1/fuel/me/push-token")
@RequiredArgsConstructor
@Tag(name = "Fuel - Push alerts", description = "Register the phone for alerts (mobile)")
public class FuelPushTokenController {

    private static final String ANY_FUEL = "hasAnyAuthority('FUEL_READ','FUEL_DRIVER','FUEL_FORECOURT','FUEL_MANAGE','FUEL_FORECOURT_MANAGE','FUEL_TRANSPORT_MANAGE','FUEL_TRUCKSTOP_MANAGE','FUEL_OPERATIONS_MANAGE','FUEL_STOCK_MANAGE','FUEL_DISTRIBUTION_MANAGE')";

    public record RegisterPushTokenRequest(@NotBlank @Size(max = 100) String deviceId, @NotBlank @Size(max = 255) String expoPushToken) {}

    private final FuelPushService push;
    private final FeatureGuard featureGuard;

    @PutMapping
    @PreAuthorize(ANY_FUEL)
    @Operation(summary = "Register this phone's push token for the signed-in user",
            description = "The token moves to this user if the phone was last used by someone else. There is no user id parameter: it is always the caller.")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterPushTokenRequest req) {
        featureGuard.requireModule("fuel");
        push.register(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req.deviceId(), req.expoPushToken());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping
    @PreAuthorize(ANY_FUEL)
    @Operation(summary = "Stop alerts for the signed-in user on this phone (call on sign-out)")
    public ResponseEntity<ApiResponse<Void>> unregister(@RequestParam("deviceId") String deviceId) {
        featureGuard.requireModule("fuel");
        push.unregister(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), deviceId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
