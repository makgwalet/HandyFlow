package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelGroupReportService;
import za.co.handyflow.platform.fuel.dto.GroupReportDtos.Report;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

/** Phase 6a: read-only group report. Anyone who can read Fuel sees volumes and stock litres; money is added only for FUEL_MARGIN_READ. */
@RestController
@RequestMapping("/api/v1/fuel/group-report")
@RequiredArgsConstructor
@Tag(name = "Fuel Group Report", description = "Read-only report across legal entities")
public class FuelGroupReportController {

    private final FuelGroupReportService report;
    private final FeatureGuard featureGuard;
    private final za.co.handyflow.platform.fuel.application.internal.FuelScopeService scopes;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<Report>> get(@RequestParam(defaultValue = "30") int days) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(report.report(TenantContext.getTenantIdAsObject(), days, canSeeMoney(),
                scopes.allowedTankIds(TenantContext.getTenantIdAsObject(), za.co.handyflow.platform.fuel.application.internal.FuelScopeService.currentUserOrNull()))));
    }

    private static boolean canSeeMoney() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> "FUEL_MARGIN_READ".equals(g.getAuthority()));
    }
}
