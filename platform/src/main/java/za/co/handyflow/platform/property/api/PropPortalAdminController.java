package za.co.handyflow.platform.property.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.property.application.internal.PropPortalService;
import za.co.handyflow.platform.property.dto.InvitePortalUserRequest;
import za.co.handyflow.platform.property.dto.PortalAccessGrantResponse;

import java.util.List;
import java.util.UUID;

/**
 * Staff-side portal-grant management: invite a lease's tenant, list/
 * revoke their access. Uses PROPERTY_READ/PROPERTY_MANAGE — same
 * permissions already gating every other action in PropertyController,
 * not a new tier (see V272's own migration comment for why).
 */
@RestController
@RequestMapping("/api/v1/property/leases/{leaseId}/portal-access")
@RequiredArgsConstructor
@Tag(name = "Property - Portal Access", description = "Tenant-portal invite management")
public class PropPortalAdminController {

    private final PropPortalService portalService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('PROPERTY_READ')")
    public ResponseEntity<ApiResponse<List<PortalAccessGrantResponse>>> list(@PathVariable UUID leaseId) {
        featureGuard.requireModule("property");
        return ResponseEntity.ok(ApiResponse.success(
                portalService.getPortalAccessGrants(TenantContext.getTenantIdAsObject(), leaseId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROPERTY_MANAGE')")
    @Operation(summary = "Invite a lease's tenant to the portal")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> invite(@PathVariable UUID leaseId,
            @Valid @RequestBody InvitePortalUserRequest req) {
        featureGuard.requireModule("property");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invite sent",
                portalService.invite(TenantContext.getTenantIdAsObject(), leaseId, req.email(),
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{grantId}/revoke")
    @PreAuthorize("hasAuthority('PROPERTY_MANAGE')")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> revoke(@PathVariable UUID leaseId,
            @PathVariable UUID grantId) {
        featureGuard.requireModule("property");
        return ResponseEntity.ok(ApiResponse.success("Access revoked",
                portalService.revoke(TenantContext.getTenantIdAsObject(), leaseId, grantId,
                        TenantContext.getCurrentUserId())));
    }
}
