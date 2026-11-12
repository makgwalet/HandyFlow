package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.GuardSupervisorService;
import za.co.handyflow.platform.security.dto.GuardSupervisorDtos.DecideSwapRequest;
import za.co.handyflow.platform.security.dto.GuardSupervisorDtos.ResolveIncidentRequest;
import za.co.handyflow.platform.security.dto.GuardSupervisorDtos.SetSiteSupervisorRequest;
import za.co.handyflow.platform.security.dto.IncidentResponse;
import za.co.handyflow.platform.security.dto.ShiftSwapResponse;
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

    @GetMapping("/api/v1/guard/supervisor/swaps")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "Swaps at my site waiting for a supervisor — site supervisors only",
            description = "Only swaps a colleague has already agreed to, and never one the caller is part of.")
    public ResponseEntity<ApiResponse<java.util.List<ShiftSwapResponse>>> swaps() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                service.swapsToDecide(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/api/v1/guard/supervisor/swaps/{id}/approve")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "Approve a swap at my site — site supervisors only",
            description = "Re-runs the scheduling checks and reassigns the shift. 403 for a swap the caller is part of; 409 if it is not waiting for a decision.")
    public ResponseEntity<ApiResponse<ShiftSwapResponse>> approveSwap(@PathVariable UUID id, @Valid @RequestBody DecideSwapRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.decideSwap(
                TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, true, req.reason())));
    }

    @PostMapping("/api/v1/guard/supervisor/swaps/{id}/reject")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "Refuse a swap at my site — site supervisors only")
    public ResponseEntity<ApiResponse<ShiftSwapResponse>> rejectSwap(@PathVariable UUID id, @Valid @RequestBody DecideSwapRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.decideSwap(
                TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, false, req.reason())));
    }

    @PostMapping("/api/v1/guard/supervisor/incidents/{id}/acknowledge")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "Acknowledge an incident at my site — site supervisors only")
    public ResponseEntity<ApiResponse<IncidentResponse>> acknowledgeIncident(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.acknowledgeIncident(
                TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id)));
    }

    @PostMapping("/api/v1/guard/supervisor/incidents/{id}/resolve")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "Resolve an incident at my site with a note on how it was dealt with — site supervisors only")
    public ResponseEntity<ApiResponse<IncidentResponse>> resolveIncident(@PathVariable UUID id, @Valid @RequestBody ResolveIncidentRequest req) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(service.resolveIncident(
                TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, req.note())));
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
