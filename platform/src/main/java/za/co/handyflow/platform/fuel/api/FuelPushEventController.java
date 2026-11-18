package za.co.handyflow.platform.fuel.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelPushEventService;
import za.co.handyflow.platform.fuel.application.internal.FuelSettingsService;
import za.co.handyflow.platform.fuel.dto.PushDtos;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;

/** A person's own alert switches, the overdue delivery policy, and the recent alert log. */
@RestController
@RequestMapping("/api/v1/fuel/push")
@RequiredArgsConstructor
public class FuelPushEventController {

    private final FuelPushEventService events;
    private final FuelSettingsService settings;
    private final FeatureGuard featureGuard;

    @GetMapping("/preferences")
    @PreAuthorize("hasAuthority('FUEL_DRIVER') or hasAuthority('FUEL_READ') or hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> preferences() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(events.preferences(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId())));
    }

    @PutMapping("/preferences")
    @PreAuthorize("hasAuthority('FUEL_DRIVER') or hasAuthority('FUEL_READ') or hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> setPreferences(@RequestBody PushDtos.Preferences req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", events.setPreferences(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req.enabled() == null ? Map.of() : req.enabled())));
    }

    @GetMapping("/overdue-policy")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<PushDtos.OverduePolicy>> overduePolicy() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(settings.overduePolicy(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/overdue-policy")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<PushDtos.OverduePolicy>> updateOverduePolicy(@Valid @RequestBody PushDtos.OverduePolicy req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", settings.updateOverduePolicy(TenantContext.getTenantIdAsObject(), req)));
    }

    /** What became of recent alerts (sent, failed, no phone, switched off), for support. */
    @GetMapping("/recent")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> recent(@RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(events.recent(TenantContext.getTenantIdAsObject(), limit)));
    }
}
