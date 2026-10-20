package za.co.handyflow.platform.fleet.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fleet.application.internal.FuelAnalyticsService;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fuel analytics. FLEET_READ to view; FLEET_MANAGE to set a vehicle's expected consumption.
 * Every period parameter is optional: {@code to} defaults to today and {@code from} to 89 days
 * before it (max 366 days).
 */
@RestController
@RequestMapping("/api/v1/fleet")
@RequiredArgsConstructor
@Tag(name = "Fleet - Fuel Analytics", description = "Consumption, abnormal-consumption flags, theft indicators, station prices")
public class FuelAnalyticsController {

    private final FuelAnalyticsService analyticsService;
    private final FeatureGuard featureGuard;

    @GetMapping("/vehicles/{vehicleId}/fuel-analytics")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "One vehicle's consumption (L/100 km, km/L, fuel cost per km) measured full-tank to full-tank, "
            + "each segment's deviation from baseline, and theft / data-quality indicators")
    public ResponseEntity<ApiResponse<VehicleFuelAnalyticsResponse>> vehicle(
            @PathVariable UUID vehicleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.getVehicleAnalytics(TenantContext.getTenantIdAsObject(), vehicleId, from, to)));
    }

    @PutMapping("/vehicles/{vehicleId}/fuel-settings")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Set a vehicle's expected L/100 km (overrides its rolling baseline) and/or its tolerance. "
            + "Nulls clear the override / restore the 20% default")
    public ResponseEntity<ApiResponse<FuelSettingsResponse>> updateSettings(
            @PathVariable UUID vehicleId, @Valid @RequestBody UpdateFuelSettingsRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Fuel settings saved",
                analyticsService.updateSettings(TenantContext.getTenantIdAsObject(), vehicleId,
                        UserContext.getCurrentUserId(), request)));
    }

    @GetMapping("/fuel-analytics/vehicles")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Fleet fuel board: one row per vehicle with consumption, spend, cost per km and flags — "
            + "thirstiest relative to its own baseline first")
    public ResponseEntity<ApiResponse<List<FleetFuelSummaryRow>>> fleet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.getFleetSummary(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/fuel-analytics/alerts")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Alert feed: abnormal consumption and theft / data-quality indicators across the fleet, newest first. "
            + "Indicators are reasons to look, not proof")
    public ResponseEntity<ApiResponse<List<FuelAlertResponse>>> alerts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.getAlerts(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/fuel-analytics/stations")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Spend and average price per litre by station and fuel type, with each station's premium over the tenant average")
    public ResponseEntity<ApiResponse<List<FuelStationSummaryRow>>> stations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.getStations(TenantContext.getTenantIdAsObject(), from, to)));
    }
}