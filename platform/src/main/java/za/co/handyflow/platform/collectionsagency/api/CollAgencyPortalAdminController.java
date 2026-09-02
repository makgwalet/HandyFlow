package za.co.handyflow.platform.collectionsagency.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyPortalService;
import za.co.handyflow.platform.collectionsagency.dto.InvitePortalUserRequest;
import za.co.handyflow.platform.collectionsagency.dto.PortalAccessGrantResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Staff-side portal-grant management: invite a client contact, list/revoke their access. */
@RestController
@RequestMapping("/api/v1/collections-agency/clients/{clientId}/portal-access")
@RequiredArgsConstructor
@Tag(name = "Collections Agency - Portal Access", description = "Client-portal invite management")
public class CollAgencyPortalAdminController {

    private final CollAgencyPortalService portalService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<List<PortalAccessGrantResponse>>> list(@PathVariable UUID clientId) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                portalService.getPortalAccessGrants(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Invite a creditor client contact to the collections portal")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> invite(@PathVariable UUID clientId,
            @Valid @RequestBody InvitePortalUserRequest req) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invite sent",
                portalService.invite(TenantContext.getTenantIdAsObject(), clientId, req.email(),
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{grantId}/revoke")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> revoke(@PathVariable UUID clientId,
            @PathVariable UUID grantId) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success("Access revoked",
                portalService.revoke(TenantContext.getTenantIdAsObject(), clientId, grantId,
                        TenantContext.getCurrentUserId())));
    }
}
