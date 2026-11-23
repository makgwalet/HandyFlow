package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.SiteAccessAdminService;
import za.co.handyflow.platform.security.application.internal.SiteScopeService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Site access administration")
public class SiteAccessAdminController {

    public record BranchAccessRequest(List<SiteAccessAdminService.BranchEntry> branches) {}

    public record RegionAccessRequest(List<UUID> regionIds) {}

    private final SiteAccessAdminService service;
    private final FeatureGuard featureGuard;

    /** Optional so a hand-built controller still works; in the application it is always there. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SiteScopeService scope;

    /** Whoever changes or lists other people's access must hold the whole company: a limited administrator could otherwise widen someone beyond their own reach. */
    private void wholeCompany() {
        SiteScopeService.checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
    }

    @GetMapping("/api/v1/security/site-access")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Everyone who is limited to some branches, clients or sites, and how many sites that leaves them",
            description = "A user with 0 visible sites sees nothing. Needs access to the whole company.")
    public ResponseEntity<ApiResponse<List<SiteAccessAdminService.LimitedUser>>> limitedUsers() {
        featureGuard.requireModule("security");
        wholeCompany();
        return ResponseEntity.ok(ApiResponse.success("OK", service.limitedUsers(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/api/v1/security/users/{userId}/branch-access")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Limit a user to the sites of some branches (replaces the previous branch list; an empty list removes the branch part)",
            description = "Each entry is a branch and a role, MANAGER or VIEWER. Branches taken away are switched off and kept on record; guard assignments are not touched. "
                    + "You cannot change your own access, and only a user with access to the whole company can change anyone's. Audited.")
    public ResponseEntity<ApiResponse<SiteScopeService.View>> setBranches(@PathVariable UUID userId, @Valid @RequestBody BranchAccessRequest r) {
        featureGuard.requireModule("security");
        wholeCompany();
        return ResponseEntity.ok(ApiResponse.success("OK", service.replaceBranches(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), userId, r.branches())));
    }

    @PutMapping("/api/v1/security/users/{userId}/region-access")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Limit a user to the sites of the branches in some regions (replaces the previous region list; an empty list removes the region part)",
            description = "A region brings in every branch it holds, including branches added later. You cannot change your own access, and only a user with access to the whole company "
                    + "can change anyone's. Audited.")
    public ResponseEntity<ApiResponse<SiteScopeService.View>> setRegions(@PathVariable UUID userId, @Valid @RequestBody RegionAccessRequest r) {
        featureGuard.requireModule("security");
        wholeCompany();
        return ResponseEntity.ok(ApiResponse.success("OK", service.replaceRegions(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), userId, r.regionIds())));
    }
}
