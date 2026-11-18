package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.Operation;
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
import za.co.handyflow.platform.fuel.application.internal.FuelDivisionSalesService;
import za.co.handyflow.platform.fuel.application.internal.FuelScopeService;
import za.co.handyflow.platform.fuel.dto.DivisionSalesDtos.Report;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/fuel/division-sales")
@RequiredArgsConstructor
@Tag(name = "Fuel Sales by Division", description = "Revenue and sales by division, read-only")
public class FuelDivisionSalesController {

    private final FuelDivisionSalesService report;
    private final FuelScopeService scopes;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    @Operation(summary = "Sales by division over the last N days, against the N days before, limited to the divisions and tanks the caller may see")
    public ResponseEntity<ApiResponse<Report>> get(@RequestParam(defaultValue = "30") int days) {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        var user = FuelScopeService.currentUserOrNull();
        return ResponseEntity.ok(ApiResponse.success(report.report(tenant, user, days, canSeeMoney(), scopes.allowedTankIds(tenant, user))));
    }

    /** The same rule as the group report: money is shown only to those who may see margin. */
    private static boolean canSeeMoney() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> "FUEL_MARGIN_READ".equals(g.getAuthority()));
    }
}
