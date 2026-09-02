package za.co.handyflow.platform.collectionsagency.api;

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
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyClientService;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyProfileService;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyClient;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyProfile;
import za.co.handyflow.platform.collectionsagency.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Foundation-layer endpoints — agency profile and creditor-client
 * portfolio. FeatureGuard-gated the same as every other separately-
 * subscribable module in this platform ("collectionsagency").
 */
@RestController
@RequestMapping("/api/v1/collections-agency")
@RequiredArgsConstructor
@Tag(name = "Collections Agency", description = "Third-party debt collections agency practice management")
public class CollAgencyController {

    private final CollAgencyProfileService profileService;
    private final CollAgencyClientService clientService;
    private final FeatureGuard featureGuard;

    // ── Agency profile ───────────────────────────────────────────────────────

    @GetMapping("/profile")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Get the agency's own practice profile, including firm Debt Collectors Act registration")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile() {
        featureGuard.requireModule("collectionsagency");
        CollAgencyProfile p = profileService.get(TenantContext.getTenantIdAsObject());
        return ResponseEntity.ok(ApiResponse.success(p == null ? null : toProfileResponse(p)));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Create or update the agency's practice profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> upsertProfile(@Valid @RequestBody UpsertProfileRequest req) {
        featureGuard.requireModule("collectionsagency");
        CollAgencyProfile p = profileService.upsert(TenantContext.getTenantIdAsObject(), req.agencyName(),
                req.firmRegistrationNumber(), req.firmRegistrationExpiryDate(), req.defaultCommissionPct(),
                req.contactEmail(), req.contactPhone(), req.physicalAddress());
        return ResponseEntity.ok(ApiResponse.success("Profile saved", toProfileResponse(p)));
    }

    // ── Creditor client portfolio ────────────────────────────────────────────

    @GetMapping("/clients")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "List active creditor clients")
    public ResponseEntity<ApiResponse<Page<ClientResponse>>> getClients(@PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                clientService.list(TenantContext.getTenantIdAsObject(), pageable).map(this::toClientResponse)));
    }

    @GetMapping("/clients/all")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "List all active creditor clients, unpaginated — for dashboards/pickers")
    public ResponseEntity<ApiResponse<List<ClientResponse>>> getAllClients() {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(clientService.listAll(TenantContext.getTenantIdAsObject())
                .stream().map(this::toClientResponse).toList()));
    }

    @GetMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<ClientResponse>> getClient(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                toClientResponse(clientService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/clients")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Onboard a new creditor client")
    public ResponseEntity<ApiResponse<ClientResponse>> createClient(@Valid @RequestBody CreateClientRequest req) {
        featureGuard.requireModule("collectionsagency");
        CollAgencyClient client = clientService.create(TenantContext.getTenantIdAsObject(), req.tradingName(),
                req.registrationNumber(), req.commissionRatePct(), req.contactName(), req.contactEmail(),
                req.contactPhone(), req.address());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Client onboarded", toClientResponse(client)));
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<ClientResponse>> updateClient(@PathVariable UUID id,
                                                                     @Valid @RequestBody UpdateClientRequest req) {
        featureGuard.requireModule("collectionsagency");
        CollAgencyClient client = clientService.update(TenantContext.getTenantIdAsObject(), id, req.tradingName(),
                req.registrationNumber(), req.commissionRatePct(), req.contactName(), req.contactEmail(),
                req.contactPhone(), req.address(), req.notes());
        return ResponseEntity.ok(ApiResponse.success("Client updated", toClientResponse(client)));
    }

    @PostMapping("/clients/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<ClientResponse>> deactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success("Client deactivated",
                toClientResponse(clientService.deactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/clients/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<ClientResponse>> reactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success("Client reactivated",
                toClientResponse(clientService.reactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @DeleteMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Soft-delete a creditor client — ADMIN only, same gating as every other irreversible-by-a-normal-user action in this module")
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable UUID id) {
        featureGuard.requireModule("collectionsagency");
        clientService.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Client deleted", null));
    }

    private ProfileResponse toProfileResponse(CollAgencyProfile p) {
        return new ProfileResponse(p.getId(), p.getAgencyName(), p.getFirmRegistrationNumber(),
                p.getFirmRegistrationExpiryDate(), p.getDefaultCommissionPct(), p.getContactEmail(),
                p.getContactPhone(), p.getPhysicalAddress());
    }

    private ClientResponse toClientResponse(CollAgencyClient c) {
        return new ClientResponse(c.getId(), c.getTradingName(), c.getRegistrationNumber(), c.getCommissionRatePct(),
                c.getContactName(), c.getContactEmail(), c.getContactPhone(), c.getAddress(), c.getTrustBalance(),
                c.getOnboardedAt(), c.getStatus(), c.getNotes());
    }
}
