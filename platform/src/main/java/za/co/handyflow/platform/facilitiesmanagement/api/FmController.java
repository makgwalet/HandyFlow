package za.co.handyflow.platform.facilitiesmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmClientService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmProfileService;
import za.co.handyflow.platform.facilitiesmanagement.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilitiesmanagement")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Profile & Clients", description = "The FM company's own profile and its client register")
public class FmController {

    private final FmProfileService profileService;
    private final FmClientService clientService;
    private final FeatureGuard featureGuard;

    // ── Profile ──────────────────────────────────────────────────────────────

    @GetMapping("/profile")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmProfileResponse>> getProfile() {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(profileService.getProfile(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmProfileResponse>> upsertProfile(@Valid @RequestBody UpsertFmProfileRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Profile saved",
                profileService.upsertProfile(TenantContext.getTenantIdAsObject(), request)));
    }

    // ── Clients ──────────────────────────────────────────────────────────────

    @GetMapping("/clients")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<Page<FmClientResponse>>> getClients(@PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(clientService.getClients(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmClientResponse>> getClient(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(clientService.getClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmClientResponse>> createClient(@Valid @RequestBody CreateFmClientRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Client created",
                clientService.createClient(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmClientResponse>> updateClient(@PathVariable UUID id, @Valid @RequestBody UpdateFmClientRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Client updated",
                clientService.updateClient(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/clients/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmClientResponse>> deactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Client deactivated",
                clientService.deactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmClientResponse>> reactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Client reactivated",
                clientService.reactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('FACILITIESMANAGEMENT_ADMIN')")
    @Operation(summary = "Soft-delete a client. Restricted to ADMIN, matching every other module's own delete-tier convention.")
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        clientService.deleteClient(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Client deleted", null));
    }
}
