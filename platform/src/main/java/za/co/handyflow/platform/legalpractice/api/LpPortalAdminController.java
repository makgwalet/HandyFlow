package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalpractice.application.internal.LpPortalService;
import za.co.handyflow.platform.legalpractice.dto.InviteClientToPortalRequest;
import za.co.handyflow.platform.legalpractice.dto.LpPortalAccessGrantResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Staff-facing portal-management console — a flatter, admin-console view
 * of the same {@code LpPortalService} operations already nested under
 * {@code LpClientController}'s {@code /clients/{id}/portal-access}
 * sub-resource, matching {@code CollAgencyPortalAdminController}'s own
 * separate-from-the-client-controller shape. Both paths call the same
 * service — no business logic is duplicated, only the URL surface.
 */
@RestController
@RequestMapping("/api/v1/legal-practice/portal/admin")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Portal Admin", description = "Staff management of client portal access")
public class LpPortalAdminController {

    private final LpPortalService portalService;
    private final FeatureGuard featureGuard;

    @GetMapping("/grants")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<List<LpPortalAccessGrantResponse>>> getGrants(@RequestParam UUID clientId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                portalService.listForClient(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping("/clients/{clientId}/grants")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Invite a client contact to the portal")
    public ResponseEntity<ApiResponse<LpPortalAccessGrantResponse>> inviteClientToPortal(
            @PathVariable UUID clientId, @Valid @RequestBody InviteClientToPortalRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Portal invite sent",
                portalService.inviteClientToPortal(TenantContext.getTenantIdAsObject(), clientId, req.inviteEmail(),
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/grants/{grantId}/revoke")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpPortalAccessGrantResponse>> revokeAccess(@PathVariable UUID grantId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Portal access revoked",
                portalService.revoke(TenantContext.getTenantIdAsObject(), grantId, TenantContext.getCurrentUserId())));
    }
}
