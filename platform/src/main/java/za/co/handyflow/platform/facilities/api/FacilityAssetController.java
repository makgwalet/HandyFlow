package za.co.handyflow.platform.facilities.api;

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
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.facilities.application.internal.FacilityAssetService;
import za.co.handyflow.platform.facilities.dto.AssetResponse;
import za.co.handyflow.platform.facilities.dto.CreateAssetRequest;
import za.co.handyflow.platform.facilities.dto.UpdateAssetRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilities/assets")
@RequiredArgsConstructor
@Tag(name = "Facilities - Assets", description = "Building asset register — HVAC, generators, fire equipment, elevators, etc.")
public class FacilityAssetController {

    private final FacilityAssetService assetService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<Page<AssetResponse>>> getAssets(
            @RequestParam(required = false) UUID siteId,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(
                assetService.getAssets(TenantContext.getTenantIdAsObject(), siteId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<AssetResponse>> getAsset(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(assetService.getAsset(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<AssetResponse>> createAsset(@Valid @RequestBody CreateAssetRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Asset registered", assetService.createAsset(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<AssetResponse>> updateAsset(@PathVariable UUID id, @RequestBody UpdateAssetRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Asset updated", assetService.updateAsset(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    @Operation(summary = "Update asset status: OPERATIONAL, DOWN, MAINTENANCE, DECOMMISSIONED")
    public ResponseEntity<ApiResponse<AssetResponse>> updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                assetService.updateStatus(TenantContext.getTenantIdAsObject(), id, body.get("status"))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        assetService.deleteAsset(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Asset deleted", null));
    }

    // ── Evidence (photos: install condition, damage, compliance) ───────────────

    @PostMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id, @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "PHOTO") String evidenceType) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached",
                evidenceFacade.attach(TenantContext.getTenantIdAsObject(), file, evidenceType, "facilities",
                        "FacilityAsset", id, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(TenantContext.getTenantIdAsObject(), "facilities", "FacilityAsset", id)));
    }
}
