package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelPosImportService;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Phase 8e (D03): the review queue and the import keys. Under /forecourt, so the Retail division rule applies. */
@RestController
@RequestMapping("/api/v1/fuel/forecourt/imports")
@RequiredArgsConstructor
@Tag(name = "Fuel Import Review", description = "Review shifts sent by pump/POS systems and manage their keys")
public class FuelImportReviewController {

    private static final String REVIEW = "hasAnyAuthority('FUEL_SHIFT_SUPERVISE','FUEL_MANAGE','FUEL_FORECOURT_MANAGE')";
    private static final String KEYS = "hasAnyAuthority('FUEL_MANAGE','FUEL_FORECOURT_MANAGE')";

    private final FuelPosImportService imports;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(REVIEW)
    public ResponseEntity<ApiResponse<List<ImportView>>> list(@RequestParam(required = false) String status, @RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(imports.list(TenantContext.getTenantIdAsObject(), status, limit)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(REVIEW)
    public ResponseEntity<ApiResponse<ImportView>> get(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(imports.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAnyAuthority('FUEL_SHIFT_SUPERVISE','FUEL_FORECOURT_MANAGE')")
    @Operation(summary = "Accept a staged shift: opens and closes it using the normal shift rules. A refusal leaves it pending")
    public ResponseEntity<ApiResponse<ImportView>> accept(@PathVariable UUID id, @Valid @RequestBody(required = false) AcceptRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Shift accepted",
                imports.accept(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('FUEL_SHIFT_SUPERVISE','FUEL_FORECOURT_MANAGE')")
    public ResponseEntity<ApiResponse<ImportView>> reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Shift rejected",
                imports.reject(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/keys")
    @PreAuthorize(KEYS)
    public ResponseEntity<ApiResponse<List<KeyView>>> keys() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(imports.listKeys(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/keys")
    @PreAuthorize(KEYS)
    @Operation(summary = "Create a key for a pump/POS system. The key is shown once")
    public ResponseEntity<ApiResponse<CreatedKey>> createKey(@Valid @RequestBody KeyRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Key created",
                imports.createKey(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), req.name())));
    }

    @PostMapping("/keys/{id}/revoke")
    @PreAuthorize(KEYS)
    public ResponseEntity<ApiResponse<KeyView>> revokeKey(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Key revoked", imports.revokeKey(TenantContext.getTenantIdAsObject(), id)));
    }
}
