package za.co.handyflow.platform.facilitiesmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPortalService;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmPortalAccessGrant;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalAccessGrantResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.InviteFmPortalUserRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilitiesmanagement/clients/{clientId}/portal-users")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Portal Admin", description = "Invite/list/revoke client portal access")
public class FmPortalAdminController {

    private final FmPortalService portalService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<List<FmPortalAccessGrantResponse>>> list(@PathVariable UUID clientId) {
        featureGuard.requireModule("facilitiesmanagement");
        List<FmPortalAccessGrantResponse> grants = portalService.listForClient(TenantContext.getTenantIdAsObject(), clientId)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(grants));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    @Operation(summary = "Invite a client contact to the facilities management portal")
    public ResponseEntity<ApiResponse<FmPortalAccessGrantResponse>> invite(
            @PathVariable UUID clientId, @Valid @RequestBody InviteFmPortalUserRequest req) {
        featureGuard.requireModule("facilitiesmanagement");
        FmPortalAccessGrant grant = portalService.invite(TenantContext.getTenantIdAsObject(), clientId,
                TenantContext.getCurrentUserId(), req.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invite sent", toResponse(grant)));
    }

    @PostMapping("/{grantId}/revoke")
    @PreAuthorize("hasAuthority('FACILITIESMANAGEMENT_ADMIN')")
    @Operation(summary = "Revoke a client contact's portal access — ADMIN-only, matching every other module's own revoke-tier convention")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable UUID clientId, @PathVariable UUID grantId) {
        featureGuard.requireModule("facilitiesmanagement");
        portalService.revoke(TenantContext.getTenantIdAsObject(), grantId, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Access revoked", null));
    }

    private FmPortalAccessGrantResponse toResponse(FmPortalAccessGrant g) {
        return new FmPortalAccessGrantResponse(g.getId(), g.getClientId(), g.getInviteEmail(), g.getStatus(),
                g.getAcceptedAt(), g.getInvitedAt());
    }
}
