package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.ProjectPortalService;
import za.co.handyflow.platform.projects.dto.PortalStatusResponse;
import za.co.handyflow.platform.projects.dto.PortalTokenRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Staff-side control of a project's client-portal link.
 * Reading status needs PM_READ; changing it needs PM_ADMIN.
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects/{projectId}/portal")
@RequiredArgsConstructor
@Tag(name = "Client Portal Admin", description = "Rotate, revoke and re-enable a project's client portal link")
public class ProjectPortalAdminController {

    private final ProjectPortalService portalService;

    @GetMapping
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Portal status — enabled, currently accessible, expiry (token is never returned here)")
    public ResponseEntity<ApiResponse<PortalStatusResponse>> status(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                portalService.getStatus(TenantContext.getTenantIdAsObject(), projectId)));
    }

    @PostMapping("/rotate-token")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Issue a new portal token — the old link stops working immediately. Body optional: { \"ttlDays\": 90 }")
    public ResponseEntity<ApiResponse<PortalStatusResponse>> rotate(
            @PathVariable UUID projectId,
            @RequestBody(required = false) @Valid PortalTokenRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Portal token rotated",
                portalService.rotateToken(TenantContext.getTenantIdAsObject(), projectId,
                        req != null ? req.ttlDays() : null)));
    }

    @PostMapping("/revoke")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Switch client portal access off")
    public ResponseEntity<ApiResponse<PortalStatusResponse>> revoke(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Portal access revoked",
                portalService.revoke(TenantContext.getTenantIdAsObject(), projectId)));
    }

    @PostMapping("/enable")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Switch client portal access back on (rotate the token if it has expired)")
    public ResponseEntity<ApiResponse<PortalStatusResponse>> enable(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Portal access enabled",
                portalService.enable(TenantContext.getTenantIdAsObject(), projectId)));
    }
}