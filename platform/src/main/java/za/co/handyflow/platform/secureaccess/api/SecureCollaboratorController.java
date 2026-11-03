package za.co.handyflow.platform.secureaccess.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.*;
import za.co.handyflow.platform.secureaccess.application.SecureCollaboratorService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Staff management of registered external collaborators, tenant-wide and shared by every module.
 * Reading needs PM_READ; changes need PM_APPROVE or PM_ADMIN (the same bar as a document controller).
 * TODO(phase 4): when Contracting/FM register providers, decide whether their roles also manage collaborators.
 */
@Validated
@RestController
@RequestMapping("/api/v1/secure-access/collaborators")
@RequiredArgsConstructor
@Tag(name = "Secure Collaborators", description = "Registered external collaborators with a standing workspace link")
public class SecureCollaboratorController {

    private static final String CONTROL = "hasAnyAuthority('PM_APPROVE','PM_ADMIN')";

    private final SecureCollaboratorService collaborators;

    @GetMapping
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Registered collaborators and the state of each account")
    public ResponseEntity<ApiResponse<List<CollaboratorView>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Success", collaborators.list(TenantContext.getTenantIdAsObject().getValue())));
    }

    @PostMapping
    @PreAuthorize(CONTROL)
    @Operation(summary = "Register a collaborator and email them their standing workspace link")
    public ResponseEntity<ApiResponse<CollaboratorView>> register(@Valid @RequestBody RegisterCollaboratorRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Collaborator registered",
                collaborators.register(TenantContext.getTenantIdAsObject().getValue(), req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/trail")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Everything done with or to this account, newest first")
    public ResponseEntity<ApiResponse<List<CollaboratorLogEntry>>> trail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success", collaborators.trail(TenantContext.getTenantIdAsObject().getValue(), id)));
    }

    @PostMapping("/{id}/reissue")
    @PreAuthorize(CONTROL)
    @Operation(summary = "Send a fresh standing link. The previous link and any open session stop working.")
    public ResponseEntity<ApiResponse<CollaboratorView>> reissue(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ReissueRequest req) {
        return ResponseEntity.ok(ApiResponse.success("New link sent",
                collaborators.reissue(TenantContext.getTenantIdAsObject().getValue(), id, req == null ? null : req.expiryDays(),
                        TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize(CONTROL)
    @Operation(summary = "Cut off this account at once; by default also revokes every share link issued to them")
    public ResponseEntity<ApiResponse<CollaboratorView>> disable(
            @PathVariable UUID id, @Valid @RequestBody DisableCollaboratorRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Account disabled",
                collaborators.disable(TenantContext.getTenantIdAsObject().getValue(), id, req.reason(), req.revokeLinks(),
                        TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize(CONTROL)
    @Operation(summary = "Turn a disabled account back on with a NEW link")
    public ResponseEntity<ApiResponse<CollaboratorView>> enable(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ReissueRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Account enabled",
                collaborators.enable(TenantContext.getTenantIdAsObject().getValue(), id, req == null ? null : req.expiryDays(),
                        TenantContext.getCurrentUserName())));
    }
}
