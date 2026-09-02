package za.co.handyflow.platform.trainingprovider.api;

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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvClientService;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvProfileService;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvClient;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProfile;
import za.co.handyflow.platform.trainingprovider.dto.ClientResponse;
import za.co.handyflow.platform.trainingprovider.dto.ProfileResponse;
import za.co.handyflow.platform.trainingprovider.dto.UpsertClientRequest;
import za.co.handyflow.platform.trainingprovider.dto.UpsertProfileRequest;

import java.util.UUID;

/**
 * Foundation-layer endpoints only — practice profile and client
 * portfolio. FeatureGuard-gated the same as every other separately-
 * subscribable module in this platform (confirmed by direct read of
 * PayrollBureauController/RecruitmentAgencyController).
 */
@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider", description = "Accredited training academy practice management")
public class TrainProvController {

    private final TrainProvProfileService profileService;
    private final TrainProvClientService clientService;
    private final FeatureGuard featureGuard;

    @GetMapping("/profile")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile() {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(toResponse(profileService.get(TenantContext.getTenantIdAsObject()))));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<ProfileResponse>> upsertProfile(@Valid @RequestBody UpsertProfileRequest req) {
        featureGuard.requireModule("trainingprovider");
        TrainProvProfile profile = profileService.upsert(TenantContext.getTenantIdAsObject(), req.tradingName(),
                req.registrationNumber(), req.accreditationBody(), req.accreditationNumber(),
                req.accreditationExpiry(), req.address(), req.phone(), req.email());
        return ResponseEntity.ok(ApiResponse.success("Profile saved", toResponse(profile)));
    }

    @GetMapping("/clients")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ClientResponse>>> getClients(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(
                clientService.list(TenantContext.getTenantIdAsObject(), status, search, pageable).map(this::toResponse)));
    }

    @GetMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<ClientResponse>> getClient(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(toResponse(clientService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/clients")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    @Operation(summary = "Add a client organization")
    public ResponseEntity<ApiResponse<ClientResponse>> createClient(@Valid @RequestBody UpsertClientRequest req) {
        featureGuard.requireModule("trainingprovider");
        TrainProvClient client = clientService.create(TenantContext.getTenantIdAsObject(), req.tradingName(),
                req.registrationNumber(), req.contactName(), req.contactEmail(), req.contactPhone(), req.address());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Client added", toResponse(client)));
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<ClientResponse>> updateClient(@PathVariable UUID id, @Valid @RequestBody UpsertClientRequest req) {
        featureGuard.requireModule("trainingprovider");
        TrainProvClient client = clientService.update(TenantContext.getTenantIdAsObject(), id, req.tradingName(),
                req.registrationNumber(), req.contactName(), req.contactEmail(), req.contactPhone(), req.address());
        return ResponseEntity.ok(ApiResponse.success("Client updated", toResponse(client)));
    }

    @PostMapping("/clients/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<ClientResponse>> deactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(toResponse(clientService.deactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/clients/{id}/reactivate")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<ClientResponse>> reactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(toResponse(clientService.reactivate(TenantContext.getTenantIdAsObject(), id))));
    }

    @DeleteMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('TRAININGPROVIDER_ADMIN')")
    @Operation(summary = "Soft-delete a client — ADMIN only")
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        clientService.softDelete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Client deleted", null));
    }

    private ProfileResponse toResponse(TrainProvProfile p) {
        return new ProfileResponse(p.getId(), p.getTradingName(), p.getRegistrationNumber(), p.getAccreditationBody(),
                p.getAccreditationNumber(), p.getAccreditationExpiry(), p.getAddress(), p.getPhone(), p.getEmail(), p.getLogoUrl());
    }

    private ClientResponse toResponse(TrainProvClient c) {
        return new ClientResponse(c.getId(), c.getClientCode(), c.getTradingName(), c.getRegistrationNumber(),
                c.getContactName(), c.getContactEmail(), c.getContactPhone(), c.getAddress(), c.getStatus(), c.getCreatedAt());
    }
}
