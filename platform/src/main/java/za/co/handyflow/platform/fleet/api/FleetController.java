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
import za.co.handyflow.platform.fleet.application.internal.FleetService;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fleet")
@RequiredArgsConstructor
@Tag(name = "Fleet", description = "Fleet and vehicle management")
public class FleetController {

    private final FleetService fleetService;
    private final FeatureGuard featureGuard;

    // ── Vehicles ──────────────────────────────────────────────────────────────

    @GetMapping("/vehicles")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<VehicleResponse>>> getVehicles(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String vehicleType,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                fleetService.getVehicles(TenantContext.getTenantIdAsObject(), status, vehicleType, pageable)));
    }

    @GetMapping("/vehicles/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<VehicleResponse>> getVehicle(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                fleetService.getVehicle(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/vehicles")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<ApiResponse<VehicleResponse>> createVehicle(
            @Valid @RequestBody CreateVehicleRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Vehicle registered",
                        fleetService.createVehicle(TenantContext.getTenantIdAsObject(), request)));
    }

    // FIX: Changed from @PatchMapping to @PutMapping.
    // PATCH triggers CORS preflight that Spring's default CORS config rejects.
    @PutMapping("/vehicles/{id}/status")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Update vehicle status: AVAILABLE, ON_TRIP, MAINTENANCE, BREAKDOWN, RETIRED")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVehicleStatusRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                fleetService.updateStatus(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/vehicles/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteVehicle(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        fleetService.deleteVehicle(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Vehicle deleted", null));
    }

    // ── Services ──────────────────────────────────────────────────────────────

    @GetMapping("/vehicles/{id}/services")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<ServiceResponse>>> getServices(
            @PathVariable UUID id,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                fleetService.getServiceHistory(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/vehicles/{id}/services")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<ApiResponse<ServiceResponse>> recordService(
            @PathVariable UUID id,
            @Valid @RequestBody CreateServiceRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Service recorded",
                        fleetService.recordService(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Trips ─────────────────────────────────────────────────────────────────

    // Global trip list — all trips across all vehicles, sorted by date
    @GetMapping("/trips")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get all trips across all vehicles (for logbook view)")
    public ResponseEntity<ApiResponse<Page<TripResponse>>> getAllTrips(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 100) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                fleetService.getAllTrips(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/vehicles/{id}/trips")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<TripResponse>>> getVehicleTrips(
            @PathVariable UUID id,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                fleetService.getTrips(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/vehicles/{id}/trips/start")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Start a trip on a vehicle — sets status to ON_TRIP")
    public ResponseEntity<ApiResponse<TripResponse>> startTrip(
            @PathVariable UUID id,
            @Valid @RequestBody StartTripRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Trip started",
                        fleetService.startTrip(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/vehicles/{id}/trips/end")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "End the active trip on a vehicle — returns to AVAILABLE")
    public ResponseEntity<ApiResponse<TripResponse>> endTrip(
            @PathVariable UUID id,
            @Valid @RequestBody EndTripRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Trip ended",
                fleetService.endTrip(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Fuel fill-ups ─────────────────────────────────────────────────────────

    @GetMapping("/vehicles/{id}/fuel")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get fuel fill-up log for a vehicle")
    public ResponseEntity<ApiResponse<Page<FuelFillupResponse>>> getFuelLog(
            @PathVariable UUID id,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                fleetService.getFuelLog(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @PostMapping("/vehicles/{id}/fuel")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Log a fuel fill-up (separate from trips)")
    public ResponseEntity<ApiResponse<FuelFillupResponse>> logFuel(
            @PathVariable UUID id,
            @Valid @RequestBody LogFuelRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fuel logged",
                        fleetService.logFuel(TenantContext.getTenantIdAsObject(), id, request)));
    }
}
