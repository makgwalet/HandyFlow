package za.co.handyflow.platform.facilities.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilities.application.internal.FacilityAccessAdminService;
import za.co.handyflow.platform.facilities.dto.SetSiteAccessRequest;
import za.co.handyflow.platform.facilities.dto.SiteAccessResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** ADMIN-only management of per-user site restrictions. Every change is audited. */
@RestController
@RequestMapping("/api/v1/facilities/access")
@RequiredArgsConstructor
@Tag(name = "Facilities - Access", description = "Per-user site restrictions")
public class FacilityAccessAdminController {

    private final FacilityAccessAdminService accessAdminService;
    private final FeatureGuard featureGuard;

    @GetMapping("/users/{userId}/sites")
    @PreAuthorize("hasAuthority('FACILITIES_ADMIN')")
    @Operation(summary = "Sites a user is restricted to (restricted=false means unrestricted)")
    public ResponseEntity<ApiResponse<SiteAccessResponse>> getSiteAccess(@PathVariable UUID userId) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(
                accessAdminService.getSiteAccess(TenantContext.getTenantIdAsObject(), userId)));
    }

    @PutMapping("/users/{userId}/sites")
    @PreAuthorize("hasAuthority('FACILITIES_ADMIN')")
    @Operation(summary = "Replace a user's site set. An empty set removes the restriction.")
    public ResponseEntity<ApiResponse<SiteAccessResponse>> setSiteAccess(
            @PathVariable UUID userId, @Valid @RequestBody SetSiteAccessRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Site access updated",
                accessAdminService.setSiteAccess(TenantContext.getTenantIdAsObject(), userId, request.siteIds())));
    }
}