package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelEligibilityService;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** Eligibility set-up: what a delivery site lets in, and the specifications of a tanker the site rules are checked against. */
@RestController
@RequestMapping("/api/v1/fuel")
@RequiredArgsConstructor
@Tag(name = "Fuel Eligibility", description = "Site access rules and tanker specifications")
public class FuelEligibilityController {

    private final FuelEligibilityService eligibility;
    private final FeatureGuard featureGuard;

    @GetMapping("/customer-sites/{siteId}/access-rule")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<SiteRuleView>> siteRule(@PathVariable UUID siteId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(eligibility.siteRule(TenantContext.getTenantIdAsObject(), siteId)));
    }

    @PutMapping("/customer-sites/{siteId}/access-rule")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<SiteRuleView>> saveSiteRule(@PathVariable UUID siteId, @Valid @RequestBody SiteRuleRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Site rule saved", eligibility.saveSiteRule(TenantContext.getTenantIdAsObject(), siteId, req, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/customer-sites/{siteId}/driver-approvals")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<java.util.List<DriverApprovalView>>> driverApprovals(@PathVariable UUID siteId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(eligibility.driverApprovals(TenantContext.getTenantIdAsObject(), siteId)));
    }

    @PutMapping("/customer-sites/{siteId}/driver-approvals/{employeeId}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<DriverApprovalView>> saveDriverApproval(@PathVariable UUID siteId, @PathVariable UUID employeeId, @Valid @RequestBody DriverApprovalRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Driver approval saved",
                eligibility.saveDriverApproval(TenantContext.getTenantIdAsObject(), siteId, employeeId, req, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/customer-sites/{siteId}/driver-approvals/{employeeId}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> removeDriverApproval(@PathVariable UUID siteId, @PathVariable UUID employeeId) {
        featureGuard.requireModule("fuel");
        eligibility.removeDriverApproval(TenantContext.getTenantIdAsObject(), siteId, employeeId);
        return ResponseEntity.ok(ApiResponse.success("Driver approval removed", null));
    }

    @GetMapping("/tankers/{id}/specs")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<TankerSpecsView>> specs(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(eligibility.specs(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/tankers/{id}/specs")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<TankerSpecsView>> saveSpecs(@PathVariable UUID id, @Valid @RequestBody TankerSpecsRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Tanker specifications saved", eligibility.saveSpecs(TenantContext.getTenantIdAsObject(), id, req)));
    }
}
