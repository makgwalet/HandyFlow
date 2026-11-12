package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelTruckStopService;
import za.co.handyflow.platform.fuel.dto.TruckStopDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** E01: Truck Stop & Driver Services. Needs the Truck Stop division (FuelDivisionInterceptor); reservations are Bookings records. */
@RestController
@RequestMapping("/api/v1/fuel/truck-stop")
@RequiredArgsConstructor
@Tag(name = "Fuel Truck Stop", description = "Truck stop services and reservations, held in Bookings")
public class FuelTruckStopController {

    private final FuelTruckStopService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/offerings")
    @PreAuthorize("hasAnyAuthority('FUEL_READ','FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<List<OfferingResponse>>> offerings() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.listOfferings(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/available-services")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<List<ServiceOption>>> availableServices() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.availableServices(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/offerings")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<OfferingResponse>> addOffering(@Valid @RequestBody OfferingRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Service added",
                service.addOffering(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/offerings/{id}/status")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<OfferingResponse>> setStatus(@PathVariable UUID id, @RequestBody OfferingStatusRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.setActive(TenantContext.getTenantIdAsObject(), id, req.active())));
    }

    @GetMapping("/offerings/{id}/slots")
    @PreAuthorize("hasAnyAuthority('FUEL_READ','FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<List<Slot>>> slots(@PathVariable UUID id, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                         @RequestParam(required = false) UUID resourceId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.slots(TenantContext.getTenantIdAsObject(), id, date, resourceId)));
    }

    @GetMapping("/reservations")
    @PreAuthorize("hasAnyAuthority('FUEL_READ','FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> reservations(@RequestParam(required = false) String vehicle,
                                                                               @RequestParam(defaultValue = "50") int limit) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(service.recent(TenantContext.getTenantIdAsObject(), vehicle, limit)));
    }

    @PostMapping("/reservations")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<ReservationResponse>> reserve(@Valid @RequestBody ReserveRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Reserved",
                service.reserve(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/reservations/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) OutcomeRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Cancelled", service.cancel(TenantContext.getTenantIdAsObject(), id, req == null ? null : req.reason())));
    }

    @PostMapping("/reservations/{id}/complete")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<ReservationResponse>> complete(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Completed", service.complete(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/reservations/{id}/no-show")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRUCKSTOP_MANAGE')")
    public ResponseEntity<ApiResponse<ReservationResponse>> noShow(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Marked as no-show", service.noShow(TenantContext.getTenantIdAsObject(), id)));
    }
}
