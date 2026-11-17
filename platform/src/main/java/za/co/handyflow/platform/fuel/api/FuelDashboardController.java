package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelDashboardService;
import za.co.handyflow.platform.fuel.application.internal.FuelScopeService;
import za.co.handyflow.platform.fuel.dto.FuelDashboardSummary;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;

/** The Fuel dashboard's figures, over the whole month, limited to the tanks the caller may see. */
@RestController
@RequestMapping("/api/v1/fuel/dashboard-summary")
@RequiredArgsConstructor
@Tag(name = "Fuel Dashboard", description = "Month and open-delivery figures for the Fuel dashboard")
public class FuelDashboardController {

    private final FuelDashboardService service;
    private final FuelScopeService scopeService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    @Operation(summary = "This month's dispatches and receipts, dip-variance alerts and open deliveries, for the tanks you may see")
    public ResponseEntity<ApiResponse<FuelDashboardSummary>> summary() {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        var allowed = scopeService.allowedTankIds(tenant, FuelScopeService.currentUserOrNull());
        return ResponseEntity.ok(ApiResponse.success(service.summary(tenant, allowed, Instant.now())));
    }
}
