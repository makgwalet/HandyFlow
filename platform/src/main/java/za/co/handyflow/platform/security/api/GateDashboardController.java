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

    private final GateDashboardService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Who is on site now across sites, today's entries and exits, and overstays; optional site")
    public ResponseEntity<ApiResponse<Dashboard>> dashboard(@RequestParam(required = false) UUID siteId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.dashboard(TenantContext.getTenantIdAsObject(), siteId)));
    }
}
