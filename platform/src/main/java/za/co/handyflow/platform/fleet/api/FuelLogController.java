package za.co.handyflow.platform.fleet.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fleet.application.internal.FuelLogService;
import za.co.handyflow.platform.fleet.dto.FleetFuelLogRow;
import za.co.handyflow.platform.fleet.dto.FleetFuelLogSummary;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.UUID;

/** The fleet-wide fuel log. FLEET_READ. The period defaults to the last 30 days (max 366). */
@RestController
@RequestMapping("/api/v1/fleet/fuel-log")
@RequiredArgsConstructor
@Tag(name = "Fleet - Fuel Log", description = "Every fuel fill-up across the fleet, searchable and paged")
public class FuelLogController {

    private final FuelLogService fuelLogService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Fill-ups across all vehicles, newest first. q matches registration, station or receipt reference")
    public ResponseEntity<ApiResponse<Page<FleetFuelLogRow>>> log(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 25) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                fuelLogService.log(TenantContext.getTenantIdAsObject(), from, to, vehicleId, q, pageable)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Count, litres, spend and average price per litre over everything the same filter matches")
    public ResponseEntity<ApiResponse<FleetFuelLogSummary>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) String q) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                fuelLogService.summary(TenantContext.getTenantIdAsObject(), from, to, vehicleId, q)));
    }
}
