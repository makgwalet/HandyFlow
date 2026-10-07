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

@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@Tag(name = "Security - Dashboard")
public class SecurityDashboardController {

    private final SecurityDashboardService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Landing dashboard: figures for now and today, what needs attention, active shifts and open incidents")
    public ResponseEntity<ApiResponse<Dashboard>> dashboard() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.dashboard(TenantContext.getTenantIdAsObject())));
    }
}
