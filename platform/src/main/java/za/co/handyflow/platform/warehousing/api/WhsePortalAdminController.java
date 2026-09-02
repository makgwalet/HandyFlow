package za.co.handyflow.platform.warehousing.api;

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
import za.co.handyflow.platform.warehousing.application.internal.WhsePortalService;
import za.co.handyflow.platform.warehousing.dto.InvitePortalUserRequest;
import za.co.handyflow.platform.warehousing.dto.PortalAccessGrantResponse;

import java.util.List;
import java.util.UUID;

/** Staff-side portal-grant management: invite a client contact, list/revoke their access. */
@RestController
@RequestMapping("/api/v1/warehousing/clients/{clientId}/portal-access")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Portal Access", description = "Client-portal invite management")
public class WhsePortalAdminController {

    private final WhsePortalService portalService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<List<PortalAccessGrantResponse>>> list(@PathVariable UUID clientId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(
                portalService.getPortalAccessGrants(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Invite a client contact to the warehousing portal")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> invite(@PathVariable UUID clientId,
            @Valid @RequestBody InvitePortalUserRequest req) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invite sent",
                portalService.invite(TenantContext.getTenantIdAsObject(), clientId, req.email(),
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{grantId}/revoke")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> revoke(@PathVariable UUID clientId,
            @PathVariable UUID grantId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Access revoked",
                portalService.revoke(TenantContext.getTenantIdAsObject(), clientId, grantId,
                        TenantContext.getCurrentUserId())));
    }
}
