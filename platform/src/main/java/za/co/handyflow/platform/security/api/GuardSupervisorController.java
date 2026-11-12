package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.GuardSupervisorService;
import za.co.handyflow.platform.security.dto.GuardSupervisorDtos.SetSiteSupervisorRequest;
import za.co.handyflow.platform.security.dto.GuardSupervisorDtos.SiteOverview;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Guard - Site supervisor", description = "Site overview for guards a manager made site supervisors (mobile)")
public class GuardSupervisorController {

    private final GuardSupervisorService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/api/v1/guard/supervisor/site")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "Overview of the site I am on shift at — site supervisors only",
            description = "Who is on duty with last scan and round counts, and open incidents from the last 24 hours. Site comes from " +
                    "the caller's own open session. 403 NOT_SITE_SUPERVISOR unless a manager has enabled it for this guard.")
    public ResponseEntity<ApiResponse<SiteOverview>> overview() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                service.siteOverview(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId())));
    }

    @PatchMapping("/api/v1/security/guards/{id}/site-supervisor")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Make a guard a site supervisor, or take it away",
            description = "A site supervisor can see the overview of the site they are on shift at, in the Guard app. Read only.")
    public ResponseEntity<ApiResponse<Void>> setSiteSupervisor(@PathVariable UUID id, @Valid @RequestBody SetSiteSupervisorRequest req) {
        featureGuard.requireModule("security");
        service.setSiteSupervisor(TenantContext.getTenantIdAsObject(), id, req.enabled());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
