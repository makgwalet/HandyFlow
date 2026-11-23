// security/api/MonthlyPackController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.MonthlyPackService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Monthly service report")
public class MonthlyPackController {

    private final MonthlyPackService pack;
    private final FeatureGuard featureGuard;

    /** Optional so a hand-built controller still works; in the application it is always there. Limits a user to the sites in their scope. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }


    @GetMapping("/api/v1/security/sites/{siteId}/monthly-pack")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "The monthly service report of a site, on South African month boundaries (default: last month)",
            description = "Cover delivered against the site's posts, shifts by status, patrol completion, incident, visitor and alarm counts, " +
                    "and Occurrence Book days signed. Counts only: no guard names, visitor details or incident text. The current month is month to date.")
    public ResponseEntity<ApiResponse<MonthlyPackService.Pack>> get(@PathVariable UUID siteId, @RequestParam(required = false) String month) {
        featureGuard.requireModule("security");
        site(siteId);
        return ResponseEntity.ok(ApiResponse.success("OK", pack.pack(TenantContext.getTenantIdAsObject(), siteId, month)));
    }
}
