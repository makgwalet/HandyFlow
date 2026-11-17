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
    private final za.co.handyflow.platform.fuel.application.internal.FuelScopeService scopes;
    private final za.co.handyflow.platform.fuel.application.internal.FuelForecourtSupplyReconciliationService reconciliation;
    private final za.co.handyflow.platform.fuel.application.internal.FuelDispatchMatchService dispatchMatching;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<Report>> get(@RequestParam(defaultValue = "30") int days) {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        var allowed = scopes.allowedDepotIds(tenant, za.co.handyflow.platform.fuel.application.internal.FuelScopeService.currentUserOrNull());
        return ResponseEntity.ok(ApiResponse.success(report.report(tenant, days, canSeeMoney(), allowed)));
    }

    /** Where each forecourt's fuel came from in the period. Litres only, so no margin permission is needed. */
    @GetMapping("/reconciliation")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.Reconciliation>> reconcile(@RequestParam(defaultValue = "30") int days) {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        var allowed = scopes.allowedDepotIds(tenant, za.co.handyflow.platform.fuel.application.internal.FuelScopeService.currentUserOrNull());
        return ResponseEntity.ok(ApiResponse.success(reconciliation.report(tenant, days, allowed)));
    }

    /** Dispatches to each forecourt against the receipts linked to them. Litres only. */
    @GetMapping("/dispatch-matching")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.MatchingReport>> matching(@RequestParam(defaultValue = "30") int days) {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        var allowed = scopes.allowedDepotIds(tenant, za.co.handyflow.platform.fuel.application.internal.FuelScopeService.currentUserOrNull());
        return ResponseEntity.ok(ApiResponse.success(dispatchMatching.report(tenant, days, allowed)));
    }

    @PostMapping("/dispatch-matching")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_STOCK_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> match(@RequestBody za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.MatchRequest req) {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        var user = za.co.handyflow.platform.fuel.application.internal.FuelScopeService.currentUserOrNull();
        dispatchMatching.match(tenant, req.dispatchId(), req.receiptId(), user, scopes.allowedDepotIds(tenant, user));
        return ResponseEntity.ok(ApiResponse.success("Receipt linked to the dispatch", null));
    }

    @DeleteMapping("/dispatch-matching/{matchId}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_STOCK_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> unmatch(@PathVariable java.util.UUID matchId) {
        featureGuard.requireModule("fuel");
        var tenant = TenantContext.getTenantIdAsObject();
        dispatchMatching.unmatch(tenant, matchId, scopes.allowedDepotIds(tenant, za.co.handyflow.platform.fuel.application.internal.FuelScopeService.currentUserOrNull()));
        return ResponseEntity.ok(ApiResponse.success("Link removed", null));
    }

    private static boolean canSeeMoney() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().anyMatch(g -> "FUEL_MARGIN_READ".equals(g.getAuthority()));
    }
}
