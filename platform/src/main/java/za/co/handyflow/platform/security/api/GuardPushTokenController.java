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
import za.co.handyflow.platform.security.application.internal.GuardPushService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/guard/me/push-token")
@RequiredArgsConstructor
@Tag(name = "Guard - Push alerts", description = "Register the phone for alerts (mobile)")
public class GuardPushTokenController {

    public record RegisterPushTokenRequest(@NotBlank String deviceHardwareId, @NotBlank String expoPushToken) {}

    private final GuardPushService pushService;
    private final FeatureGuard featureGuard;

    @PutMapping
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "Register this phone's push token for the signed-in guard",
            description = "The token moves to this guard if the phone was last used by someone else. Always the caller's own; no guard id parameter.")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterPushTokenRequest req) {
        featureGuard.requireModule("security");
        pushService.register(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req.deviceHardwareId(), req.expoPushToken());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "Stop alerts for the signed-in guard on this phone (call on sign-out)")
    public ResponseEntity<ApiResponse<Void>> unregister(@RequestParam("deviceHardwareId") String deviceHardwareId) {
        featureGuard.requireModule("security");
        pushService.unregister(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), deviceHardwareId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
