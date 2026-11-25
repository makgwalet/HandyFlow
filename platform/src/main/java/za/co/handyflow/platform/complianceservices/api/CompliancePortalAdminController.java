package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.complianceservices.application.internal.CompliancePortalAdminService;
import za.co.handyflow.platform.complianceservices.dto.PortalDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance-services/clients/{clientId}/portal-users")
@RequiredArgsConstructor
@Tag(name = "Compliance Services - Portal Access", description = "Invite, list and revoke a client's portal contacts")
public class CompliancePortalAdminController {

    private final CompliancePortalAdminService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_READ','COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<List<GrantView>>> list(@PathVariable UUID clientId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('COMPLIANCE_SERVICES_MANAGE','COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<GrantView>> invite(@PathVariable UUID clientId, @Valid @RequestBody InviteRequest request) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invite created",
                service.invite(TenantContext.getTenantIdAsObject(), clientId, request.email(), TenantContext.getCurrentUserId())));
    }

    /** Taking access away is an administrator's call. It takes effect on the person's next request. */
    @PostMapping("/{grantId}/revoke")
    @PreAuthorize("hasAuthority('COMPLIANCE_SERVICES_ADMIN')")
    public ResponseEntity<ApiResponse<GrantView>> revoke(@PathVariable UUID clientId, @PathVariable UUID grantId) {
        featureGuard.requireModule("complianceservices");
        return ResponseEntity.ok(ApiResponse.success("Access revoked",
                service.revoke(TenantContext.getTenantIdAsObject(), clientId, grantId, TenantContext.getCurrentUserId())));
    }
}
