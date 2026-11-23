package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.BillingPreviewService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Billing preview")
public class BillingPreviewController {

    private final BillingPreviewService service;
    private final FeatureGuard featureGuard;

    /** Optional so a hand-built controller still works; in the application it is always there. Limits a user to the sites in their scope. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    @GetMapping("/api/v1/security/sites/{siteId}/billing-preview")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "What the site would be invoiced for a month: completed shifts × the contract rate (a preview, not an invoice)",
            description = "month is 2026-10 (default: last month). basis is SCHEDULED (default, as payroll counts hours today) or ACTUAL (clocked hours where recorded). "
                    + "Only COMPLETED shifts are priced; other shifts are counted but not billed. VAT is not calculated.")
    public ResponseEntity<ApiResponse<BillingPreviewService.Result>> preview(@PathVariable UUID siteId,
                                                                             @RequestParam(required = false) String month,
                                                                             @RequestParam(required = false) String basis) {
        featureGuard.requireModule("security");
        site(siteId);
        return ResponseEntity.ok(ApiResponse.success("OK", service.preview(TenantContext.getTenantIdAsObject(), siteId, month, basis)));
    }
}
