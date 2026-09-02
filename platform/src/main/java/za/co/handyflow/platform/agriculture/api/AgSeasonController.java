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
import za.co.handyflow.platform.agriculture.application.internal.AgSeasonService;
import za.co.handyflow.platform.agriculture.dto.CreateSeasonRequest;
import za.co.handyflow.platform.agriculture.dto.SeasonResponse;
import za.co.handyflow.platform.agriculture.dto.UpdateSeasonRequest;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** Farm-scoped planting seasons — mirrors AgProductionAreaController's own shape. */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Seasons", description = "Farm-scoped planting seasons")
public class AgSeasonController {

    private final AgSeasonService seasonService;
    private final FeatureGuard featureGuard;

    @GetMapping("/farms/{farmId}/seasons")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<Page<SeasonResponse>>> getSeasonsForFarm(
            @PathVariable UUID farmId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                seasonService.getSeasonsForFarm(TenantContext.getTenantIdAsObject(), farmId, status, pageable)));
    }

    @PostMapping("/farms/{farmId}/seasons")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    @Operation(summary = "Define a new planting season for a farm")
    public ResponseEntity<ApiResponse<SeasonResponse>> createSeason(
            @PathVariable UUID farmId, @Valid @RequestBody CreateSeasonRequest request) {
        featureGuard.requireModule("agriculture");
        if (!farmId.equals(request.farmId())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("farmId in path and body must match"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Season created",
                seasonService.createSeason(TenantContext.getTenantIdAsObject(), request)));
    }

    @GetMapping("/seasons/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_READ')")
    public ResponseEntity<ApiResponse<SeasonResponse>> getSeason(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success(
                seasonService.getSeason(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/seasons/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<SeasonResponse>> updateSeason(
            @PathVariable UUID id, @Valid @RequestBody UpdateSeasonRequest request) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Season updated",
                seasonService.updateSeason(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/seasons/{id}/activate")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<SeasonResponse>> activateSeason(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Season activated",
                seasonService.activateSeason(TenantContext.getTenantIdAsObject(), id)));
    }

    @PatchMapping("/seasons/{id}/close")
    @PreAuthorize("hasAuthority('AGRICULTURE_MANAGE')")
    public ResponseEntity<ApiResponse<SeasonResponse>> closeSeason(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        return ResponseEntity.ok(ApiResponse.success("Season closed",
                seasonService.closeSeason(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/seasons/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSeason(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        seasonService.deleteSeason(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Season deleted", null));
    }
}
