package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelPortalService;
import za.co.handyflow.platform.fuel.dto.PortalDtos.GrantResponse;
import za.co.handyflow.platform.fuel.dto.PortalDtos.InviteRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Staff side. The path deliberately has no "/portal/" segment, which would route it to the portal token filter. */
@RestController
@RequestMapping("/api/v1/fuel/customer-accounts/{accountId}/portal-access")
@RequiredArgsConstructor
@Tag(name = "Fuel Portal Access", description = "Invite and revoke customer portal access")
public class FuelPortalAdminController {

    private final FuelPortalService portal;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<GrantResponse>>> list(@PathVariable UUID accountId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(portal.list(TenantContext.getTenantIdAsObject(), accountId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_PORTAL_MANAGE')")
    public ResponseEntity<ApiResponse<GrantResponse>> invite(@PathVariable UUID accountId, @Valid @RequestBody InviteRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invite sent",
                portal.invite(TenantContext.getTenantIdAsObject(), accountId, req.email(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{grantId}/revoke")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_PORTAL_MANAGE')")
    public ResponseEntity<ApiResponse<GrantResponse>> revoke(@PathVariable UUID accountId, @PathVariable UUID grantId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Access revoked",
                portal.revoke(TenantContext.getTenantIdAsObject(), accountId, grantId, TenantContext.getCurrentUserId())));
    }
}
