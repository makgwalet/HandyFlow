package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.CollaboratorAccessService;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Staff management of registered external collaborators. Tenant-wide (not tied to one project, which is why the
 * path is NOT under /api/v1/projects: the project access interceptor only understands project-scoped URLs). Changes
 * need PM_APPROVE or PM_ADMIN, the same bar as a document controller; reading needs PM_READ.
 */
@Validated
@RestController
@RequestMapping("/api/v1/project-collaborators")
@RequiredArgsConstructor
@Tag(name = "Collaborators", description = "Registered external collaborators with a standing workspace link")
public class CollaboratorController {

    private static final String CONTROL = "hasAnyAuthority('PM_APPROVE','PM_ADMIN')";

    private final CollaboratorAccessService collaborators;

    @GetMapping
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Registered collaborators and the state of each account")
    public ResponseEntity<ApiResponse<List<CollaboratorResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Success", collaborators.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize(CONTROL)
    @Operation(summary = "Register a collaborator and email them their standing workspace link")
    public ResponseEntity<ApiResponse<CollaboratorResponse>> register(@Valid @RequestBody RegisterCollaboratorRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Collaborator registered",
                collaborators.register(TenantContext.getTenantIdAsObject(), req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/trail")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Everything done with or to this account, newest first")
    public ResponseEntity<ApiResponse<List<CollaboratorResponse.LogEntry>>> trail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success", collaborators.trail(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/reissue")
    @PreAuthorize(CONTROL)
    @Operation(summary = "Send a fresh standing link. The previous link and any open session stop working.")
    public ResponseEntity<ApiResponse<CollaboratorResponse>> reissue(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ReissueLinkRequest req) {
        return ResponseEntity.ok(ApiResponse.success("New link sent",
                collaborators.reissue(TenantContext.getTenantIdAsObject(), id, req == null ? null : req.expiryDays(),
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize(CONTROL)
    @Operation(summary = "Cut off this account at once; by default also revokes every transmittal link issued to them")
    public ResponseEntity<ApiResponse<CollaboratorResponse>> disable(
            @PathVariable UUID id, @Valid @RequestBody DisableCollaboratorRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Account disabled",
                collaborators.disable(TenantContext.getTenantIdAsObject(), id, req.reason(), req.revokeTransmittalLinks(),
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize(CONTROL)
    @Operation(summary = "Turn a disabled account back on with a NEW link")
    public ResponseEntity<ApiResponse<CollaboratorResponse>> enable(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ReissueLinkRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Account enabled",
                collaborators.enable(TenantContext.getTenantIdAsObject(), id, req == null ? null : req.expiryDays(),
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }
}
