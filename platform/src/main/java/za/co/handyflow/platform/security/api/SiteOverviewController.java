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

    @GetMapping("/{id}/overview")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Site detail page: guards on site now, next shifts, recent incidents, checkpoint scan counts")
    public ResponseEntity<ApiResponse<Overview>> overview(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(overviewService.overview(TenantContext.getTenantIdAsObject(), id)));
    }
}
