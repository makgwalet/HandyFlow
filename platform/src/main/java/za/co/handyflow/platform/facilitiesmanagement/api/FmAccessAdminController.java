package za.co.handyflow.platform.facilitiesmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmAccessAdminService;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmClientAccessResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.SetFmClientAccessRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** ADMIN-only management of per-user client restrictions. Every change is audited. */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement/access")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Access", description = "Per-user client restrictions")
public class FmAccessAdminController {

    private final FmAccessAdminService accessAdminService;
    private final FeatureGuard featureGuard;

    @GetMapping("/users/{userId}/clients")
    @PreAuthorize("hasAuthority('FACILITIESMANAGEMENT_ADMIN')")
    @Operation(summary = "Clients a user is restricted to (restricted=false means unrestricted)")
    public ResponseEntity<ApiResponse<FmClientAccessResponse>> getClientAccess(@PathVariable UUID userId) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                accessAdminService.getClientAccess(TenantContext.getTenantIdAsObject(), userId)));
    }

    @PutMapping("/users/{userId}/clients")
    @PreAuthorize("hasAuthority('FACILITIESMANAGEMENT_ADMIN')")
    @Operation(summary = "Replace a user's client set. An empty set removes the restriction.")
    public ResponseEntity<ApiResponse<FmClientAccessResponse>> setClientAccess(
            @PathVariable UUID userId, @Valid @RequestBody SetFmClientAccessRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Client access updated",
                accessAdminService.setClientAccess(TenantContext.getTenantIdAsObject(), userId, request.clientIds())));
    }
}