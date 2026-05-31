// fleet/api/FleetController.java

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
@RequestMapping("/api/v1/fleet/vehicles")
@RequiredArgsConstructor
@Tag(name = "Fleet - Vehicles", description = "Fleet and vehicle management")
public class FleetController {

    private final FleetService fleetService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all vehicles, optionally filter by status")
    public ResponseEntity<ApiResponse<Page<VehicleResponse>>> getVehicles(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("fleet");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                fleetService.getVehicles(tenantId, status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<VehicleResponse>> getVehicle(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                fleetService.getVehicle(tenantId, id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Register a new vehicle — validates registration is unique")
    public ResponseEntity<ApiResponse<VehicleResponse>> createVehicle(
            @Valid @RequestBody CreateVehicleRequest request
    ) {
        featureGuard.requireModule("fleet");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Vehicle registered",
                        fleetService.createVehicle(tenantId, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Update vehicle status: AVAILABLE, IN_USE, SERVICE, BREAKDOWN, RETIRED")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVehicleStatusRequest request
    ) {
        featureGuard.requireModule("fleet");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                fleetService.updateStatus(tenantId, id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteVehicle(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        var tenantId = TenantContext.getTenantIdAsObject();
        fleetService.deleteVehicle(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Vehicle deleted", null));
    }

    // ── Services ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/services")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<ServiceResponse>>> getServices(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("fleet");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                fleetService.getServiceHistory(tenantId, id, pageable)));
    }

    @PostMapping("/{id}/services")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Record a vehicle service — updates odometer and service tracking")
    public ResponseEntity<ApiResponse<ServiceResponse>> recordService(
            @PathVariable UUID id,
            @Valid @RequestBody CreateServiceRequest request
    ) {
        featureGuard.requireModule("fleet");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Service recorded",
                        fleetService.recordService(tenantId, id, request)));
    }

    // ── Trips ─────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/trips")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<TripResponse>>> getTrips(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("fleet");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                fleetService.getTrips(tenantId, id, pageable)));
    }

    @PostMapping("/{id}/trips/start")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Start a trip — sets vehicle status to IN_USE")
    public ResponseEntity<ApiResponse<TripResponse>> startTrip(
            @PathVariable UUID id,
            @Valid @RequestBody StartTripRequest request
    ) {
        featureGuard.requireModule("fleet");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Trip started",
                        fleetService.startTrip(tenantId, id, request)));
    }

    @PostMapping("/{id}/trips/end")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "End the active trip — updates odometer, sets vehicle to AVAILABLE")
    public ResponseEntity<ApiResponse<TripResponse>> endTrip(
            @PathVariable UUID id,
            @Valid @RequestBody EndTripRequest request
    ) {
        featureGuard.requireModule("fleet");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success("Trip ended",
                fleetService.endTrip(tenantId, id, request)));
    }
}