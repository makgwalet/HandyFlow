package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelToleranceService;
import za.co.handyflow.platform.fuel.dto.ShiftControlDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

/** Tolerances by depot and fuel type, and the repeated-variance rule. The configuration permission; shared foundation, so no division rule. */
@RestController
@RequestMapping("/api/v1/fuel/shift-controls")
@RequiredArgsConstructor
@Tag(name = "Fuel Shift Controls", description = "Tolerances by site and product, and repeated-variance alerts")
public class FuelShiftControlController {

    private static final String CONFIG = "hasAuthority('FUEL_CONFIGURATION_MANAGE')";
    private final FuelToleranceService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CONFIG)
    public ResponseEntity<ApiResponse<Controls>> get() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/overrides")
    @PreAuthorize(CONFIG)
    public ResponseEntity<ApiResponse<OverrideView>> save(@Valid @RequestBody OverrideRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", service.save(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/overrides")
    @PreAuthorize(CONFIG)
    public ResponseEntity<ApiResponse<Void>> remove(@RequestParam String scope, @RequestParam String key) {
        featureGuard.requireModule("fuel");
        service.remove(TenantContext.getTenantIdAsObject(), scope, key);
        return ResponseEntity.ok(ApiResponse.success("Removed", null));
    }

    @PutMapping("/repeat-policy")
    @PreAuthorize(CONFIG)
    public ResponseEntity<ApiResponse<Policy>> policy(@Valid @RequestBody PolicyRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Saved", service.savePolicy(TenantContext.getTenantIdAsObject(), req.repeatCount(), req.repeatDays(), TenantContext.getCurrentUserId())));
    }
}
