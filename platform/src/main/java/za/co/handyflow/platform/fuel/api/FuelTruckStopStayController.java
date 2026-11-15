package za.co.handyflow.platform.fuel.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelTruckStopStayService;
import za.co.handyflow.platform.fuel.dto.StayDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Phase 7d: overnight stays at the truck stop. Under /truck-stop, so the server requires the Truck Stop division. */
@RestController
@RequestMapping("/api/v1/fuel/truck-stop")
@RequiredArgsConstructor
public class FuelTruckStopStayController {

    private final FuelTruckStopStayService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    @GetMapping("/parking-settings")
    @PreAuthorize("hasAnyAuthority('FUEL_READ','FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<SettingsView>> settings() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.settings(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/parking-settings")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<SettingsView>> saveSettings(@RequestBody SettingsRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Parking settings saved", service.saveSettings(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/stays")
    @PreAuthorize("hasAnyAuthority('FUEL_READ','FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<List<StayView>>> list(@RequestParam(defaultValue = "100") int limit) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), limit, today())));
    }

    @GetMapping("/stays/occupancy")
    @PreAuthorize("hasAnyAuthority('FUEL_READ','FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<Occupancy>> occupancy(@RequestParam(required = false) LocalDate date) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.occupancy(TenantContext.getTenantIdAsObject(), date != null ? date : today())));
    }

    @GetMapping("/stays/capacity")
    @PreAuthorize("hasAnyAuthority('FUEL_READ','FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<CapacityView>> capacity(@RequestParam(required = false) LocalDate from, @RequestParam(defaultValue = "14") int days) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.capacity(TenantContext.getTenantIdAsObject(), from != null ? from : today(), days, today())));
    }

    @PostMapping("/stays")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<StayView>> book(@Valid @RequestBody BookRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Stay booked", service.book(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/stays/{id}/check-in")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<StayView>> checkIn(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Checked in", service.checkIn(TenantContext.getTenantIdAsObject(), id, today())));
    }

    @PostMapping("/stays/{id}/check-out")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<StayView>> checkOut(@PathVariable UUID id, @RequestBody(required = false) CheckOutRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Checked out", service.checkOut(TenantContext.getTenantIdAsObject(), id, req, today())));
    }

    @PutMapping("/stays/{id}/departure")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<StayView>> departure(@PathVariable UUID id, @Valid @RequestBody DepartureRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Departure changed", service.changeDeparture(TenantContext.getTenantIdAsObject(), id, req, today())));
    }

    @PostMapping("/stays/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<StayView>> cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Stay cancelled", service.cancel(TenantContext.getTenantIdAsObject(), id, req, today())));
    }

    @PostMapping("/stays/{id}/no-show")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<StayView>> noShow(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Marked as no-show", service.noShow(TenantContext.getTenantIdAsObject(), id, today())));
    }
}
