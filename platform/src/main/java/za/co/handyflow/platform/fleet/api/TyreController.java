package za.co.handyflow.platform.fleet.api;

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
import za.co.handyflow.platform.fleet.application.internal.TyreService;
import za.co.handyflow.platform.fleet.domain.model.TyreStatus;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

/**
 * Tyre management. FLEET_READ to view; FLEET_MANAGE for every change.
 * There is no delete: a tyre that is finished with is scrapped, which keeps its history.
 */
@RestController
@RequestMapping("/api/v1/fleet")
@RequiredArgsConstructor
@Tag(name = "Fleet - Tyres", description = "Tyre register: fitment history, rotation, tread, retreads, cost per km")
public class TyreController {

    private final TyreService tyreService;
    private final FeatureGuard featureGuard;

    @GetMapping("/tyres")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "List tyres by serial number; filter by status (IN_STOCK, FITTED, AWAITING_RETREAD, SCRAPPED)")
    public ResponseEntity<ApiResponse<Page<TyreResponse>>> list(
            @RequestParam(required = false) TyreStatus status, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(tyreService.list(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/tyres/{id}")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "A tyre with its fitment history, tread measurements, retreads, lifetime km, total cost and cost per km")
    public ResponseEntity<ApiResponse<TyreDetailResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(tyreService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/tyres")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Register a tyre into stock. Serial numbers are unique per tenant")
    public ResponseEntity<ApiResponse<TyreDetailResponse>> create(@Valid @RequestBody SaveTyreRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tyre registered",
                tyreService.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/tyres/{id}")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<TyreDetailResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody SaveTyreRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Tyre updated",
                tyreService.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/tyres/{id}/fit")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Fit an in-stock tyre to a vehicle position (FL, FR, RL, RR, A2LO, SPARE, ...). One tyre per position")
    public ResponseEntity<ApiResponse<TyreDetailResponse>> fit(
            @PathVariable UUID id, @Valid @RequestBody FitTyreRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Tyre fitted",
                tyreService.fit(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/tyres/{id}/remove")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Take a tyre off its vehicle: back to IN_STOCK, AWAITING_RETREAD, or SCRAPPED. Banks the km it ran")
    public ResponseEntity<ApiResponse<TyreDetailResponse>> remove(
            @PathVariable UUID id, @Valid @RequestBody RemoveTyreRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Tyre removed",
                tyreService.remove(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/tyres/{id}/measurements")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Record a tread-depth reading (and optional pressure). Notifies admins when a tyre crosses "
            + "the replace-soon (3 mm) or legal-minimum (1 mm) line")
    public ResponseEntity<ApiResponse<TyreDetailResponse>> measure(
            @PathVariable UUID id, @Valid @RequestBody RecordTreadMeasurementRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Measurement recorded",
                tyreService.recordMeasurement(TenantContext.getTenantIdAsObject(), id,
                        UserContext.getCurrentUserId(), request)));
    }

    @PostMapping("/tyres/{id}/retread")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Complete a retread on an AWAITING_RETREAD tyre: back to stock with a fresh tread; the cost joins its lifetime cost")
    public ResponseEntity<ApiResponse<TyreDetailResponse>> completeRetread(
            @PathVariable UUID id, @Valid @RequestBody CompleteRetreadRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Retread recorded",
                tyreService.completeRetread(TenantContext.getTenantIdAsObject(), id,
                        UserContext.getCurrentUserId(), request)));
    }

    @PostMapping("/tyres/{id}/scrap")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Scrap a tyre that is not on a vehicle (a fitted one is scrapped by removing it with outcome SCRAPPED)")
    public ResponseEntity<ApiResponse<TyreDetailResponse>> scrap(
            @PathVariable UUID id, @Valid @RequestBody ScrapAssetRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Tyre scrapped",
                tyreService.scrap(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Per vehicle ───────────────────────────────────────────────────────────

    @GetMapping("/vehicles/{vehicleId}/tyres")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "The tyres currently on a vehicle, by position, with tread status and lifetime km")
    public ResponseEntity<ApiResponse<List<TyreResponse>>> forVehicle(@PathVariable UUID vehicleId) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                tyreService.forVehicle(TenantContext.getTenantIdAsObject(), vehicleId)));
    }

    @PostMapping("/vehicles/{vehicleId}/tyres/rotate")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Rotate tyres on a vehicle in one step, swaps included. Banks each tyre's km and starts new fitments")
    public ResponseEntity<ApiResponse<List<TyreResponse>>> rotate(
            @PathVariable UUID vehicleId, @Valid @RequestBody RotateTyresRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Tyres rotated",
                tyreService.rotate(TenantContext.getTenantIdAsObject(), vehicleId, request)));
    }
}