package za.co.handyflow.platform.agriculture.api;

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
import za.co.handyflow.platform.agriculture.application.internal.AgSpeciesService;
import za.co.handyflow.platform.agriculture.dto.CreateSpeciesRequest;
import za.co.handyflow.platform.agriculture.dto.SpeciesResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateSpeciesRequest;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Tenant-scoped species catalogue — NOT farm-scoped, since a tenant's
 * species list is shared across every farm it operates. See AgSpecies's
 * own Javadoc for why breed is a plain string field rather than a second
 * catalogue entity here.
 */
@RestController
@RequestMapping("/api/v1/agriculture/species")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Species", description = "Tenant species catalogue")
public class AgSpeciesController {

    private final AgSpeciesService speciesService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<SpeciesResponse>>> getSpecies(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                speciesService.getSpecies(TenantContext.getTenantIdAsObject(), category, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<SpeciesResponse>> getSpeciesById(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                speciesService.getSpeciesById(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Add a species to the tenant's catalogue")
    public ResponseEntity<ApiResponse<SpeciesResponse>> createSpecies(@Valid @RequestBody CreateSpeciesRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Species created",
                speciesService.createSpecies(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<SpeciesResponse>> updateSpecies(
            @PathVariable UUID id, @Valid @RequestBody UpdateSpeciesRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Species updated",
                speciesService.updateSpecies(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<SpeciesResponse>> deactivateSpecies(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Species deactivated",
                speciesService.deactivateSpecies(TenantContext.getTenantIdAsObject(), id)));
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<SpeciesResponse>> reactivateSpecies(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Species reactivated",
                speciesService.reactivateSpecies(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSpecies(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        speciesService.deleteSpecies(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Species deleted", null));
    }
}
