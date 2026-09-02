package za.co.handyflow.platform.bookkeeping.api;

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
import za.co.handyflow.platform.bookkeeping.application.internal.BkClientService;
import za.co.handyflow.platform.bookkeeping.application.internal.BkProfileService;
import za.co.handyflow.platform.bookkeeping.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookkeeping")
@RequiredArgsConstructor
@Tag(name = "Bookkeeping - Profile & Clients", description = "The bookkeeping practice's own profile and its client register")
public class BkController {

    private final BkProfileService profileService;
    private final BkClientService clientService;
    private final FeatureGuard featureGuard;

    // ── Profile ──────────────────────────────────────────────────────────────

    @GetMapping("/profile")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkProfileResponse>> getProfile() {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(profileService.getProfile(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkProfileResponse>> upsertProfile(@Valid @RequestBody UpsertBkProfileRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Profile saved",
                profileService.upsertProfile(TenantContext.getTenantIdAsObject(), request)));
    }

    // ── Clients ──────────────────────────────────────────────────────────────

    @GetMapping("/clients")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<BkClientResponse>>> getClients(@PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(clientService.getClients(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkClientResponse>> getClient(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(clientService.getClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkClientResponse>> createClient(@Valid @RequestBody CreateBkClientRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Client created",
                clientService.createClient(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkClientResponse>> updateClient(@PathVariable UUID id, @Valid @RequestBody UpdateBkClientRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Client updated",
                clientService.updateClient(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/clients/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkClientResponse>> deactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Client deactivated",
                clientService.deactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkClientResponse>> reactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Client reactivated",
                clientService.reactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('BOOKKEEPING_ADMIN')")
    @Operation(summary = "Soft-delete a client. Restricted to ADMIN, matching every other module's own delete-tier convention.")
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        clientService.deleteClient(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Client deleted", null));
    }
}
