// security/api/SecurityDashboardController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.SecurityDashboardService;
import za.co.handyflow.platform.security.dto.SecurityDashboardDtos.Dashboard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@Tag(name = "Security - Dashboard")
public class SecurityDashboardController {

    private final SecurityDashboardService service;
    private final FeatureGuard featureGuard;


    /** Optional so a controller built by hand still works; in the application it is always there. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    /** Only a user with access to the whole company may use this: the records are not tied to one site. */
    private void wholeCompany() {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
    }

    private void child(za.co.handyflow.platform.security.application.internal.SiteScopeService.Kind kind, UUID id) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkChild(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), kind, id);
    }

    private java.util.Set<UUID> allowed() {
        return za.co.handyflow.platform.security.application.internal.SiteScopeService.allowedSites(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Landing dashboard: figures for now and today, what needs attention, active shifts and open incidents")
    public ResponseEntity<ApiResponse<Dashboard>> dashboard() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.dashboard(TenantContext.getTenantIdAsObject(), allowed())));
    }
}
