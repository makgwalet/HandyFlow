package za.co.handyflow.platform.warehousing.api;

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
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseClientService;
import za.co.handyflow.platform.warehousing.application.internal.WhseProfileService;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;
import za.co.handyflow.platform.warehousing.domain.model.WhseProfile;
import za.co.handyflow.platform.warehousing.dto.*;

import java.util.List;
import java.util.UUID;

/** Foundation-layer endpoints — operator profile and client portfolio. FeatureGuard-gated ("warehousing"). */
@RestController
@RequestMapping("/api/v1/warehousing")
@RequiredArgsConstructor
@Tag(name = "Warehousing", description = "3PL / public warehousing operator management")
public class WhseController {

    private final WhseProfileService profileService;
    private final WhseClientService clientService;
    private final FeatureGuard featureGuard;

    // ── Operator profile ─────────────────────────────────────────────────────

    @GetMapping("/profile")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Get the operator's own warehouse profile and default rate card")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile() {
        featureGuard.requireModule("warehousing");
        WhseProfile p = profileService.get(TenantContext.getTenantIdAsObject());
        return ResponseEntity.ok(ApiResponse.success(p == null ? null : toProfileResponse(p)));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Create or update the operator's warehouse profile and default rate card")
    public ResponseEntity<ApiResponse<ProfileResponse>> upsertProfile(@Valid @RequestBody UpsertProfileRequest req) {
        featureGuard.requireModule("warehousing");
        WhseProfile p = profileService.upsert(TenantContext.getTenantIdAsObject(), req.warehouseName(),
                req.registrationNumber(), req.defaultStorageRatePerUnitPerMonth(), req.defaultReceivingFeePerUnit(),
                req.defaultPickFeePerUnit(), req.defaultPackFeePerOrder(), req.contactEmail(), req.contactPhone(),
                req.physicalAddress());
        return ResponseEntity.ok(ApiResponse.success("Profile saved", toProfileResponse(p)));
    }

    // ── Client portfolio ─────────────────────────────────────────────────────

    @GetMapping("/clients")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "List active clients")
    public ResponseEntity<ApiResponse<Page<ClientResponse>>> getClients(@PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(
                clientService.list(TenantContext.getTenantIdAsObject(), pageable).map(this::toClientResponse)));
    }

    @GetMapping("/clients/all")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "List all active clients, unpaginated — for dashboards/pickers")
    public ResponseEntity<ApiResponse<List<ClientResponse>>> getAllClients() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(clientService.listAll(TenantContext.getTenantIdAsObject())
                .stream().map(this::toClientResponse).toList()));
    }

    @GetMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<ClientResponse>> getClient(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(
                toClientResponse(clientService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/clients")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Onboard a new client")
    public ResponseEntity<ApiResponse<ClientResponse>> createClient(@Valid @RequestBody CreateClientRequest req) {
        featureGuard.requireModule("warehousing");
        WhseClient client = clientService.create(TenantContext.getTenantIdAsObject(), req.tradingName(),
                req.registrationNumber(), req.storageRatePerUnitPerMonth(), req.receivingFeePerUnit(),
                req.pickFeePerUnit(), req.packFeePerOrder(), req.contactName(), req.contactEmail(),
                req.contactPhone(), req.address());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Client onboarded", toClientResponse(client)));
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<ClientResponse>> updateClient(@PathVariable UUID id,
                                                                      @Valid @RequestBody UpdateClientRequest req) {
        featureGuard.requireModule("warehousing");
        WhseClient client = clientService.update(TenantContext.getTenantIdAsObject(), id, req.tradingName(),
                req.registrationNumber(), req.storageRatePerUnitPerMonth(), req.receivingFeePerUnit(),
                req.pickFeePerUnit(), req.packFeePerOrder(), req.contactName(), req.contactEmail(),
                req.contactPhone(), req.address(), req.notes());
        return ResponseEntity.ok(ApiResponse.success("Client updated", toClientResponse(client)));
    }

    @PostMapping("/clients/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<ClientResponse>> deactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Client deactivated",
                toClientResponse(clientService.deactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/clients/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<ClientResponse>> reactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Client reactivated",
                toClientResponse(clientService.reactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @DeleteMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('WAREHOUSING_ADMIN')")
    @Operation(summary = "Soft-delete a client — ADMIN only, same gating as every other irreversible action in this module")
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        clientService.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Client deleted", null));
    }

    private ProfileResponse toProfileResponse(WhseProfile p) {
        return new ProfileResponse(p.getId(), p.getWarehouseName(), p.getRegistrationNumber(),
                p.getDefaultStorageRatePerUnitPerMonth(), p.getDefaultReceivingFeePerUnit(),
                p.getDefaultPickFeePerUnit(), p.getDefaultPackFeePerOrder(), p.getContactEmail(),
                p.getContactPhone(), p.getPhysicalAddress());
    }

    private ClientResponse toClientResponse(WhseClient c) {
        return new ClientResponse(c.getId(), c.getTradingName(), c.getRegistrationNumber(),
                c.getStorageRatePerUnitPerMonth(), c.getReceivingFeePerUnit(), c.getPickFeePerUnit(),
                c.getPackFeePerOrder(), c.getContactName(), c.getContactEmail(), c.getContactPhone(),
                c.getAddress(), c.getOnboardedAt(), c.getStatus(), c.getNotes());
    }
}
