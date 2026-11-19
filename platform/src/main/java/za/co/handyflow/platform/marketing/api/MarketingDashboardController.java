package za.co.handyflow.platform.marketing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.marketing.application.internal.MarketingDashboardService;
import za.co.handyflow.platform.marketing.dto.DashboardDtos.Dashboard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/marketing/analytics")
@RequiredArgsConstructor
@Tag(name = "Marketing Dashboard")
public class MarketingDashboardController {

    private final MarketingDashboardService dashboard;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('MARKETING_READ')")
    @Operation(summary = "Dashboard aggregates: current vs previous period, daily series, contact sources")
    public ResponseEntity<ApiResponse<Dashboard>> get(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.success(dashboard.dashboard(TenantContext.getTenantIdAsObject(), days)));
    }
}
