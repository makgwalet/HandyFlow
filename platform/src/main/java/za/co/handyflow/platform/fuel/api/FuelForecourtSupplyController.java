package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelForecourtSupplyReportService;
import za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.Report;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

/** Phase 8d: forecourt sales by supplying depot. Under /forecourt so the Retail division rule applies. Money only with FUEL_MARGIN_READ. */
@RestController
@RequestMapping("/api/v1/fuel/forecourt/supply-report")
@RequiredArgsConstructor
@Tag(name = "Fuel Forecourt Supply", description = "Forecourt sales grouped by supplying depot")
public class FuelForecourtSupplyController {

    private final FuelForecourtSupplyReportService report;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<Report>> get(@RequestParam(defaultValue = "30") int days) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(report.report(TenantContext.getTenantIdAsObject(), days, canSeeMoney())));
    }

    private static boolean canSeeMoney() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> "FUEL_MARGIN_READ".equals(g.getAuthority()));
    }
}
