// security/api/SiteOverviewController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.SiteOverviewService;
import za.co.handyflow.platform.security.dto.SiteOverviewDtos.Overview;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security/sites")
@RequiredArgsConstructor
@Tag(name = "Security - Site overview")
public class SiteOverviewController {

    private final SiteOverviewService overviewService;
    private final FeatureGuard featureGuard;

    /** Optional so a hand-built controller still works; in the application it is always there. Limits a user to the sites in their scope. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    /** A site outside the user's scope answers exactly like a site that does not exist. */
    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    @GetMapping("/{id}/overview")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Site detail page: guards on site now, next shifts, recent incidents, checkpoint scan counts")
    public ResponseEntity<ApiResponse<Overview>> overview(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        site(id);
        return ResponseEntity.ok(ApiResponse.success(overviewService.overview(TenantContext.getTenantIdAsObject(), id)));
    }
}
