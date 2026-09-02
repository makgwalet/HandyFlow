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
import za.co.handyflow.platform.agriculture.application.internal.AgCropTypeService;
import za.co.handyflow.platform.agriculture.dto.CreateCropTypeRequest;
import za.co.handyflow.platform.agriculture.dto.CropTypeResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateCropTypeRequest;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Tenant-scoped crop catalogue — NOT farm-scoped, mirroring
 * AgSpeciesController exactly (see AgCropType's own Javadoc for why).
 */
@RestController
@RequestMapping("/api/v1/agriculture/crop-types")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Crop Types", description = "Tenant crop catalogue")
public class AgCropTypeController {

    private final AgCropTypeService cropTypeService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<CropTypeResponse>>> getCropTypes(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                cropTypeService.getCropTypes(TenantContext.getTenantIdAsObject(), category, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<CropTypeResponse>> getCropType(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                cropTypeService.getCropType(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Add a crop type to the tenant's catalogue")
    public ResponseEntity<ApiResponse<CropTypeResponse>> createCropType(@Valid @RequestBody CreateCropTypeRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Crop type created",
                cropTypeService.createCropType(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<CropTypeResponse>> updateCropType(
            @PathVariable UUID id, @Valid @RequestBody UpdateCropTypeRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Crop type updated",
                cropTypeService.updateCropType(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<CropTypeResponse>> deactivateCropType(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Crop type deactivated",
                cropTypeService.deactivateCropType(TenantContext.getTenantIdAsObject(), id)));
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<CropTypeResponse>> reactivateCropType(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Crop type reactivated",
                cropTypeService.reactivateCropType(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCropType(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        cropTypeService.deleteCropType(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Crop type deleted", null));
    }
}
