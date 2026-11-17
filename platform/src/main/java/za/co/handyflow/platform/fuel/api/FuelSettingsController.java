package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelSettingsService;
import za.co.handyflow.platform.fuel.dto.FuelSettingsDtos;
import za.co.handyflow.platform.fuel.dto.FuelWholesaleDtos;
import za.co.handyflow.platform.fuel.dto.FuelSettingsDtos.SettingsRequest;
import za.co.handyflow.platform.fuel.dto.FuelSettingsDtos.SettingsResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/fuel/settings")
@RequiredArgsConstructor
@Tag(name = "Fuel Settings", description = "Cost-to-serve rates and delivery tolerance")
public class FuelSettingsController {

    private final FuelSettingsService settings;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<SettingsResponse>> get() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(settings.get(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/controls")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<FuelWholesaleDtos.ControlsResponse>> controls() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(settings.controls(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/controls")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<FuelWholesaleDtos.ControlsResponse>> updateControls(@RequestBody FuelWholesaleDtos.ControlsRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", settings.updateControls(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/emergency-contact")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<FuelSettingsDtos.EmergencyContact>> updateEmergencyContact(@Valid @RequestBody FuelSettingsDtos.EmergencyContact req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", settings.updateEmergencyContact(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<SettingsResponse>> update(@Valid @RequestBody SettingsRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", settings.update(TenantContext.getTenantIdAsObject(), req)));
    }
}
