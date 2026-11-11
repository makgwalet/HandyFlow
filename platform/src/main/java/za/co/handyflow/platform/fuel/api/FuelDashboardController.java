package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelDashboardService;
import za.co.handyflow.platform.fuel.dto.DashboardResponse;
import za.co.handyflow.platform.fuel.dto.MarginResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

/** Stage 4D: manager's overview. Margin is a separate endpoint because it needs the margin permission. */
@RestController
@RequestMapping("/api/v1/fuel/dashboard")
@RequiredArgsConstructor
@Tag(name = "Fuel Dashboard", description = "Overview and delivery margin")
public class FuelDashboardController {

    private final FuelDashboardService dashboard;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<DashboardResponse>> overview() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(dashboard.overview(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/margin")
    @PreAuthorize("hasAuthority('FUEL_MARGIN_READ')")
    public ResponseEntity<ApiResponse<MarginResponse>> margin(@RequestParam(defaultValue = "30") int days) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(dashboard.margin(TenantContext.getTenantIdAsObject(), days)));
    }
}
