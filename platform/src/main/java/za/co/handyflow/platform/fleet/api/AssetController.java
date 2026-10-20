package za.co.handyflow.platform.fleet.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fleet.application.internal.AssetService;
import za.co.handyflow.platform.fleet.domain.model.AssetClass;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fleet assets: classes, equipment profiles, hour meters, hour-based servicing, trailers and utilisation.
 * FLEET_READ to view; FLEET_MANAGE for every change.
 */
@RestController
@RequestMapping("/api/v1/fleet")
@RequiredArgsConstructor
@Tag(name = "Fleet - Assets", description = "Tractors, plant, forklifts, trailers: hour meters, hour-based service, utilisation")
public class AssetController {

    private final AssetService assetService;
    private final FeatureGuard featureGuard;

    @GetMapping("/assets")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "All fleet assets with their class (a vehicle with no profile is a ROAD_VEHICLE); filter by assetClass")
    public ResponseEntity<ApiResponse<Page<AssetSummaryResponse>>> list(
            @RequestParam(required = false) AssetClass assetClass,
            @PageableDefault(size = 50, sort = "registration", direction = Sort.Direction.ASC) Pageable pageable) {
        featureGuard.requireModule("fleet");
        // The repository query fixes the ordering by registration; only the page window is taken from the request.
        Pageable window = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(ApiResponse.success(
                assetService.list(TenantContext.getTenantIdAsObject(), assetClass, window)));
    }

    @GetMapping("/vehicles/{vehicleId}/equipment")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "A vehicle as a fleet asset: hour meters, hour-based service status, cost per engine hour, "
            + "trailers it hauls, implements hitched to it (configured=false for a plain road vehicle)")
    public ResponseEntity<ApiResponse<EquipmentProfileResponse>> get(@PathVariable UUID vehicleId) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(assetService.getProfile(TenantContext.getTenantIdAsObject(), vehicleId)));
    }

    @PutMapping("/vehicles/{vehicleId}/equipment")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Configure a vehicle as a fleet asset (class, charge-out rate, target hours per day, hour-based service interval, site...)")
    public ResponseEntity<ApiResponse<EquipmentProfileResponse>> save(
            @PathVariable UUID vehicleId, @Valid @RequestBody SaveEquipmentProfileRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Equipment profile saved",
                assetService.saveProfile(TenantContext.getTenantIdAsObject(), vehicleId, request)));
    }

    @PostMapping("/vehicles/{vehicleId}/equipment/meters")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Record engine / PTO / hydraulic hour readings. Meters only count up; PTO and hydraulic cannot exceed engine hours. "
            + "Notifies admins when the reading makes the asset due for service")
    public ResponseEntity<ApiResponse<EquipmentProfileResponse>> recordMeters(
            @PathVariable UUID vehicleId, @Valid @RequestBody RecordMetersRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Meters recorded",
                assetService.recordMeters(TenantContext.getTenantIdAsObject(), vehicleId,
                        UserContext.getCurrentUserId(), request)));
    }

    @GetMapping("/vehicles/{vehicleId}/equipment/meters")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "The asset's meter-reading history, newest first")
    public ResponseEntity<ApiResponse<Page<MeterReadingResponse>>> meterHistory(
            @PathVariable UUID vehicleId, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                assetService.meterHistory(TenantContext.getTenantIdAsObject(), vehicleId,
                        org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))));
    }

    @PostMapping("/vehicles/{vehicleId}/equipment/service-done")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Restart the hour-based service interval for a service done outside a work order "
            + "(completing a SERVICE work order does this automatically)")
    public ResponseEntity<ApiResponse<EquipmentProfileResponse>> serviceDone(
            @PathVariable UUID vehicleId, @Valid @RequestBody(required = false) RecordServiceDoneRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Service interval restarted",
                assetService.recordServiceDone(TenantContext.getTenantIdAsObject(), vehicleId,
                        request != null ? request : new RecordServiceDoneRequest(null))));
    }

    @PostMapping("/vehicles/{trailerId}/equipment/couple")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Couple a TRAILER to the vehicle hauling it (one host per trailer; a host can haul several)")
    public ResponseEntity<ApiResponse<EquipmentProfileResponse>> couple(
            @PathVariable UUID trailerId, @Valid @RequestBody CoupleTrailerRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Trailer coupled",
                assetService.couple(TenantContext.getTenantIdAsObject(), trailerId, request.hostVehicleId())));
    }

    @PostMapping("/vehicles/{trailerId}/equipment/uncouple")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<EquipmentProfileResponse>> uncouple(@PathVariable UUID trailerId) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Trailer uncoupled",
                assetService.uncouple(TenantContext.getTenantIdAsObject(), trailerId)));
    }

    // ── Utilisation ───────────────────────────────────────────────────────────

    @GetMapping("/vehicles/{vehicleId}/equipment/utilisation")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Hours worked and utilisation % for one asset over a period (default last 30 days, max 366), "
            + "plus charge-out value and approximate litres per hour")
    public ResponseEntity<ApiResponse<UtilisationResponse>> utilisation(
            @PathVariable UUID vehicleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                assetService.utilisation(TenantContext.getTenantIdAsObject(), vehicleId, from, to)));
    }

    @GetMapping("/assets/utilisation")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Utilisation board for every hours-based asset (tractor, plant, forklift, specialised), most-used first")
    public ResponseEntity<ApiResponse<List<UtilisationResponse>>> fleetUtilisation(
            @RequestParam(required = false) AssetClass assetClass,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                assetService.fleetUtilisation(TenantContext.getTenantIdAsObject(), assetClass, from, to)));
    }
}