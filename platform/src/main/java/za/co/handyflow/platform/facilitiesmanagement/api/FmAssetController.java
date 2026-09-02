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
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmAssetService;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmAssetRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmAssetResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpdateFmAssetRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilitiesmanagement/assets")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Assets", description = "Client building asset register — HVAC, generators, fire equipment, elevators, etc.")
public class FmAssetController {

    private final FmAssetService assetService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<Page<FmAssetResponse>>> getAssets(
            @RequestParam(required = false) UUID siteId,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                assetService.getAssets(TenantContext.getTenantIdAsObject(), siteId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmAssetResponse>> getAsset(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(assetService.getAsset(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmAssetResponse>> createAsset(@Valid @RequestBody CreateFmAssetRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Asset registered", assetService.createAsset(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmAssetResponse>> updateAsset(@PathVariable UUID id, @RequestBody UpdateFmAssetRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Asset updated", assetService.updateAsset(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    @Operation(summary = "Update asset status: OPERATIONAL, DOWN, MAINTENANCE, DECOMMISSIONED")
    public ResponseEntity<ApiResponse<FmAssetResponse>> updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                assetService.updateStatus(TenantContext.getTenantIdAsObject(), id, body.get("status"))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        assetService.deleteAsset(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Asset deleted", null));
    }

    // ── Evidence (photos: install condition, damage, compliance) ───────────────

    @PostMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(
            @PathVariable UUID id, @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "PHOTO") String evidenceType) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached",
                evidenceFacade.attach(TenantContext.getTenantIdAsObject(), file, evidenceType, "facilitiesmanagement",
                        "FmAsset", id, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> getEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                evidenceFacade.listFor(TenantContext.getTenantIdAsObject(), "facilitiesmanagement", "FmAsset", id)));
    }
}
