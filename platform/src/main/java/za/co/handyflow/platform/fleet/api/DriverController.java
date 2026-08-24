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
import za.co.handyflow.platform.fleet.application.internal.DriverService;
import za.co.handyflow.platform.fleet.dto.CreateDriverRequest;
import za.co.handyflow.platform.fleet.dto.DriverResponse;
import za.co.handyflow.platform.fleet.dto.UpdateDriverRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.UUID;

/**
 * FIX: backlog 1.7/12.1 — see FleetController's own Javadoc for the
 * full rationale; this is the mirror fix for Driver records. Same
 * three-tier split: FLEET_READ, FLEET_MANAGE, and FLEET_ADMIN
 * specifically for delete.
 */
@RestController
@RequestMapping("/api/v1/fleet/drivers")
@RequiredArgsConstructor
@Tag(name = "Fleet - Drivers", description = "Driver records, licence and PrDP compliance tracking")
public class DriverController {

    private final DriverService driverService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<ApiResponse<Page<DriverResponse>>> getDrivers(@PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                driverService.getDrivers(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<ApiResponse<DriverResponse>> getDriver(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                driverService.getDriver(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Register a new driver, with optional licence/PrDP compliance dates")
    public ResponseEntity<ApiResponse<DriverResponse>> createDriver(@Valid @RequestBody CreateDriverRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Driver registered",
                        driverService.createDriver(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Update a driver's details and compliance dates")
    public ResponseEntity<ApiResponse<DriverResponse>> updateDriver(
            @PathVariable UUID id, @Valid @RequestBody UpdateDriverRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Driver updated",
                driverService.updateDriver(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<DriverResponse>> deactivateDriver(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Driver deactivated",
                driverService.setStatus(TenantContext.getTenantIdAsObject(), id, false)));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<DriverResponse>> reactivateDriver(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Driver reactivated",
                driverService.setStatus(TenantContext.getTenantIdAsObject(), id, true)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteDriver(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        driverService.deleteDriver(TenantContext.getTenantIdAsObject(), id, UserContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Driver deleted", null));
    }
}