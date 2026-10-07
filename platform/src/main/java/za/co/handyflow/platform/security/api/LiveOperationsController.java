// security/api/LiveOperationsController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.GuardLocationService;
import za.co.handyflow.platform.security.application.internal.LiveOperationsService;
import za.co.handyflow.platform.security.dto.CurrentLocationResponse;
import za.co.handyflow.platform.security.dto.LiveGuardResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Live Operations reads. Mapped at /api/v1/security with full paths. The site locations route used to sit inside
 * DeviceSessionController, whose class-level /sessions prefix made its real URL
 * /api/v1/security/sessions/api/v1/security/sites/{id}/guards/locations, so the Live Map never reached it.
 */
@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@Tag(name = "Security - Live operations")
public class LiveOperationsController {

    private final LiveOperationsService liveService;
    private final GuardLocationService guardLocationService;
    private final FeatureGuard featureGuard;

    @GetMapping("/live/guards")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Every guard on an active shift with last position and last scan; optional siteId")
    public ResponseEntity<ApiResponse<List<LiveGuardResponse>>> liveGuards(@RequestParam(required = false) UUID siteId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(liveService.liveGuards(TenantContext.getTenantIdAsObject(), siteId)));
    }

    @GetMapping("/sites/{siteId}/guards/locations")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Current positions of guards at a site, each with a stale flag")
    public ResponseEntity<ApiResponse<List<CurrentLocationResponse>>> locations(@PathVariable UUID siteId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(guardLocationService.getCurrentLocationsForSite(TenantContext.getTenantIdAsObject(), siteId)));
    }
}
