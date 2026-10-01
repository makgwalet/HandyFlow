package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.agriculture.application.internal.AgDashboardService;
import za.co.handyflow.platform.agriculture.dto.AgDashboardResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/agriculture/dashboard")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Dashboard", description = "Tenant-wide overview across all active farms")
public class AgDashboardController {

    private final AgDashboardService dashboardService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    @Operation(summary = "All-farms dashboard: totals, farm types and locations, crops in production, livestock by species and the ranked attention list",
            description = "Covers every ACTIVE farm in one call. Counts only what Agriculture records: there is no revenue, " +
                    "labour-cost, equipment-cost or weather data, so none is returned.")
    public ResponseEntity<ApiResponse<AgDashboardResponse>> getDashboard() {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getTenantDashboard(TenantContext.getTenantIdAsObject())));
    }
}
