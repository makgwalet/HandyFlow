package za.co.handyflow.platform.bookkeeping.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.bookkeeping.application.internal.BkPortalService;
import za.co.handyflow.platform.bookkeeping.domain.model.BkPortalAccessGrant;
import za.co.handyflow.platform.bookkeeping.dto.BkPortalAccessGrantResponse;
import za.co.handyflow.platform.bookkeeping.dto.InviteBkPortalUserRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookkeeping/clients/{clientId}/portal-users")
@RequiredArgsConstructor
@Tag(name = "Bookkeeping - Portal Admin", description = "Invite/list/revoke client portal access")
public class BkPortalAdminController {

    private final BkPortalService portalService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<List<BkPortalAccessGrantResponse>>> list(@PathVariable UUID clientId) {
        featureGuard.requireModule("bookkeeping");
        List<BkPortalAccessGrantResponse> grants = portalService.listForClient(TenantContext.getTenantIdAsObject(), clientId)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(grants));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    @Operation(summary = "Invite a client contact to the bookkeeping portal")
    public ResponseEntity<ApiResponse<BkPortalAccessGrantResponse>> invite(
            @PathVariable UUID clientId, @Valid @RequestBody InviteBkPortalUserRequest req) {
        featureGuard.requireModule("bookkeeping");
        BkPortalAccessGrant grant = portalService.invite(TenantContext.getTenantIdAsObject(), clientId,
                TenantContext.getCurrentUserId(), req.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invite sent", toResponse(grant)));
    }

    @PostMapping("/{grantId}/revoke")
    @PreAuthorize("hasAuthority('BOOKKEEPING_ADMIN')")
    @Operation(summary = "Revoke a client contact's portal access — ADMIN-only, matching every other module's own revoke-tier convention")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable UUID clientId, @PathVariable UUID grantId) {
        featureGuard.requireModule("bookkeeping");
        portalService.revoke(TenantContext.getTenantIdAsObject(), grantId, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Access revoked", null));
    }

    private BkPortalAccessGrantResponse toResponse(BkPortalAccessGrant g) {
        return new BkPortalAccessGrantResponse(g.getId(), g.getClientId(), g.getInviteEmail(), g.getStatus(),
                g.getAcceptedAt(), g.getInvitedAt());
    }
}
