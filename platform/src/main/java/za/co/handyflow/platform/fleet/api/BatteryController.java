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
import za.co.handyflow.platform.fleet.application.internal.BatteryService;
import za.co.handyflow.platform.fleet.domain.model.BatteryStatus;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

/** Battery management. FLEET_READ to view; FLEET_MANAGE for every change. No delete — scrap instead. */
@RestController
@RequestMapping("/api/v1/fleet")
@RequiredArgsConstructor
@Tag(name = "Fleet - Batteries", description = "Battery register: installations, voltage tests, warranty")
public class BatteryController {

    private final BatteryService batteryService;
    private final FeatureGuard featureGuard;

    @GetMapping("/batteries")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "List batteries by serial number; filter by status (IN_STOCK, INSTALLED, FAULTY, SCRAPPED)")
    public ResponseEntity<ApiResponse<Page<BatteryResponse>>> list(
            @RequestParam(required = false) BatteryStatus status, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(batteryService.list(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/batteries/{id}")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "A battery with its installation history, voltage tests, warranty position (and whether a claim is possible) and cost per month")
    public ResponseEntity<ApiResponse<BatteryDetailResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(batteryService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/batteries")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Register a battery into stock. Warranty from an expiry date, or purchase date + months")
    public ResponseEntity<ApiResponse<BatteryDetailResponse>> create(@Valid @RequestBody SaveBatteryRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Battery registered",
                batteryService.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/batteries/{id}")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<BatteryDetailResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody SaveBatteryRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Battery updated",
                batteryService.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/batteries/{id}/install")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Install an in-stock battery on a vehicle (position defaults to 1; use 2 for a second battery)")
    public ResponseEntity<ApiResponse<BatteryDetailResponse>> install(
            @PathVariable UUID id, @Valid @RequestBody InstallBatteryRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Battery installed",
                batteryService.install(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/batteries/{id}/remove")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Take a battery off its vehicle: back to IN_STOCK, FAULTY (awaiting warranty claim) or SCRAPPED")
    public ResponseEntity<ApiResponse<BatteryDetailResponse>> remove(
            @PathVariable UUID id, @Valid @RequestBody RemoveBatteryRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Battery removed",
                batteryService.remove(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/batteries/{id}/tests")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Record a resting-voltage test. GOOD from 12.4 V, WEAK from 12.0 V, otherwise FAILED (24 V batteries at double)")
    public ResponseEntity<ApiResponse<BatteryDetailResponse>> test(
            @PathVariable UUID id, @Valid @RequestBody RecordBatteryTestRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Test recorded",
                batteryService.recordTest(TenantContext.getTenantIdAsObject(), id,
                        UserContext.getCurrentUserId(), request)));
    }

    @PostMapping("/batteries/{id}/scrap")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Scrap a battery that is not on a vehicle (an installed one is scrapped by removing it with outcome SCRAPPED)")
    public ResponseEntity<ApiResponse<BatteryDetailResponse>> scrap(
            @PathVariable UUID id, @Valid @RequestBody ScrapAssetRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Battery scrapped",
                batteryService.scrap(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @GetMapping("/vehicles/{vehicleId}/batteries")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "The batteries currently installed on a vehicle, with last test result and warranty")
    public ResponseEntity<ApiResponse<List<BatteryResponse>>> forVehicle(@PathVariable UUID vehicleId) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                batteryService.forVehicle(TenantContext.getTenantIdAsObject(), vehicleId)));
    }
}