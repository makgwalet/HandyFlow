package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelSettingsService;
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

    @PutMapping
    @PreAuthorize("hasAuthority('FUEL_MANAGE')")
    public ResponseEntity<ApiResponse<SettingsResponse>> update(@Valid @RequestBody SettingsRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", settings.update(TenantContext.getTenantIdAsObject(), req)));
    }
}
