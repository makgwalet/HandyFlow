package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.SiteScopeService;
import za.co.handyflow.platform.security.application.internal.SlaService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Security - Incident service levels")
@RestController
@RequestMapping("/api/v1/security/sla")
@RequiredArgsConstructor
public class SlaController {

    public record TargetRequest(UUID siteId, String severity, Integer acknowledgeMinutes, Integer resolveMinutes) {}

    private final SlaService service;
    private final SiteScopeService scope;

    @GetMapping("/targets")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Service-level targets: the company defaults and each site's own. A user limited to some sites sees only those sites' targets")
    public ResponseEntity<ApiResponse<SlaService.TargetsView>> targets() {
        TenantId t = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(service.targets(t, SiteScopeService.allowedSites(scope, t, TenantContext.getCurrentUserId()))));
    }

    @PutMapping("/targets")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Set a target for a severity: for one site, or as the company default when no site is given. Whole-company only")
    public ResponseEntity<ApiResponse<SlaService.TargetsView>> save(@RequestBody TargetRequest req) {
        TenantId t = TenantContext.getTenantIdAsObject();
        UUID user = TenantContext.getCurrentUserId();
        SiteScopeService.checkUnrestricted(scope, t, user);
        return ResponseEntity.ok(ApiResponse.success(service.save(t, user, req.siteId(), req.severity(), req.acknowledgeMinutes(), req.resolveMinutes())));
    }

    @DeleteMapping("/targets")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Remove a target. Incidents it covered are then judged by the next one down (site, then company default) or not tracked")
    public ResponseEntity<ApiResponse<SlaService.TargetsView>> remove(@RequestParam(required = false) UUID siteId, @RequestParam String severity) {
        TenantId t = TenantContext.getTenantIdAsObject();
        UUID user = TenantContext.getCurrentUserId();
        SiteScopeService.checkUnrestricted(scope, t, user);
        return ResponseEntity.ok(ApiResponse.success(service.remove(t, user, siteId, severity)));
    }

    @GetMapping("/live")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Open incidents that have breached a target or are close to it, worst first")
    public ResponseEntity<ApiResponse<List<SlaService.LiveItem>>> live() {
        TenantId t = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(service.live(t, SiteScopeService.allowedSites(scope, t, TenantContext.getCurrentUserId()))));
    }

    @GetMapping("/report")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "How incidents recorded between two dates did against their targets, per site and severity")
    public ResponseEntity<ApiResponse<SlaService.Report>> report(@RequestParam LocalDate from, @RequestParam LocalDate to, @RequestParam(required = false) UUID siteId) {
        TenantId t = TenantContext.getTenantIdAsObject();
        UUID user = TenantContext.getCurrentUserId();
        SiteScopeService.checkChoice(scope, t, user, siteId);
        return ResponseEntity.ok(ApiResponse.success(service.report(t, SiteScopeService.allowedSites(scope, t, user), from, to, siteId)));
    }
}
