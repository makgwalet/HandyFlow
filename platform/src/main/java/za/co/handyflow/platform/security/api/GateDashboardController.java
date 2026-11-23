// security/api/GateDashboardController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.GateDashboardService;
import za.co.handyflow.platform.security.dto.GateDashboardDtos.Dashboard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security/gate")
@RequiredArgsConstructor
@Tag(name = "Security - Gate dashboard")
public class GateDashboardController {

    /** Optional so a hand-built controller still works; in the application it is always there. Limits a user to the sites in their scope. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    /** A site outside the user's scope answers exactly like a site that does not exist. */
    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    /** The sites the user may see: null means the whole company. */
    private java.util.Set<UUID> allowed() {
        return za.co.handyflow.platform.security.application.internal.SiteScopeService.allowedSites(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
    }

    /** For a view that spans every site and cannot be narrowed yet: only a user with the whole company may use it. */
    private void wholeCompany() {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
    }

    private final GateDashboardService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Who is on site now across sites, today's entries and exits, and overstays; optional site")
    public ResponseEntity<ApiResponse<Dashboard>> dashboard(@RequestParam(required = false) UUID siteId) {
        featureGuard.requireModule("security");
        if (siteId != null) site(siteId);
        return ResponseEntity.ok(ApiResponse.success(service.dashboard(TenantContext.getTenantIdAsObject(), siteId, allowed())));
    }
}
