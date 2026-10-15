package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthSettingsService;
import za.co.handyflow.platform.earthmoving.dto.SaveSettingsRequest;
import za.co.handyflow.platform.earthmoving.dto.SettingsResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

@RestController
@RequestMapping("/api/v1/earthmoving/settings")
@RequiredArgsConstructor
@Tag(name = "Earthmoving settings")
public class EarthSettingsController {

    private final EarthSettingsService settings;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "Company settings for Earthmoving")
    public ResponseEntity<ApiResponse<SettingsResponse>> get() {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Settings", settings.get(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Set the hire value from which a signed contract is expected (null switches it off)")
    public ResponseEntity<ApiResponse<SettingsResponse>> save(@RequestBody SaveSettingsRequest req) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Settings saved",
                settings.setContractThreshold(TenantContext.getTenantIdAsObject(), req.contractThreshold(), UserContext.getCurrentUserId())));
    }
}
