package za.co.handyflow.platform.trainingprovider.api;

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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvPortalService;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvPortalAccessGrant;
import za.co.handyflow.platform.trainingprovider.dto.InvitePortalUserRequest;
import za.co.handyflow.platform.trainingprovider.dto.PortalAccessGrantResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/clients/{clientId}/portal-users")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Portal Admin", description = "Invite/revoke client portal access")
public class TrainProvPortalAdminController {

    private final TrainProvPortalService portalService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<List<PortalAccessGrantResponse>>> list(@PathVariable UUID clientId) {
        featureGuard.requireModule("trainingprovider");
        List<PortalAccessGrantResponse> grants = portalService.listForClient(TenantContext.getTenantIdAsObject(), clientId)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(grants));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    @Operation(summary = "Invite a client contact to the training portal")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> invite(@PathVariable UUID clientId, @Valid @RequestBody InvitePortalUserRequest req) {
        featureGuard.requireModule("trainingprovider");
        TrainProvPortalAccessGrant grant = portalService.invite(TenantContext.getTenantIdAsObject(), clientId, req.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invite sent", toResponse(grant)));
    }

    @PostMapping("/{grantId}/revoke")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable UUID clientId, @PathVariable UUID grantId) {
        featureGuard.requireModule("trainingprovider");
        portalService.revoke(TenantContext.getTenantIdAsObject(), grantId);
        return ResponseEntity.ok(ApiResponse.success("Access revoked", null));
    }

    private PortalAccessGrantResponse toResponse(TrainProvPortalAccessGrant g) {
        return new PortalAccessGrantResponse(g.getId(), g.getClientId(), g.getInviteEmail(), g.getStatus(),
                g.getAcceptedAt(), g.getCreatedAt());
    }
}
