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
import za.co.handyflow.platform.fuel.application.internal.FuelDeliveryExceptionService;
import za.co.handyflow.platform.fuel.application.internal.FuelTransportService;
import za.co.handyflow.platform.fuel.dto.DeliveryExceptionDtos.ExceptionView;
import za.co.handyflow.platform.fuel.dto.DeliveryExceptionDtos.FailedAttemptRequest;
import za.co.handyflow.platform.fuel.dto.DeliveryExceptionDtos.WindowRequest;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Stage 3: tankers, allocation, planning board and the driver's own loads. */
@RestController
@RequestMapping("/api/v1/fuel")
@RequiredArgsConstructor
@Tag(name = "Fuel Transport", description = "Tankers, driver allocation, planning and driver loads")
public class FuelTransportController {

    private final FuelTransportService transport;
    private final FuelDeliveryExceptionService exceptionService;
    private final FeatureGuard featureGuard;
    private final za.co.handyflow.platform.fuel.application.internal.FuelArrivalCheckService arrivalChecks;

    @GetMapping("/tankers")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<TankerResponse>>> tankers() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(transport.listTankers(TenantContext.getTenantIdAsObject())));
    }

    /** C03: Fleet vehicles a tanker can be linked to. Needs the same permission as editing a tanker, since it lists Fleet data. */
    @GetMapping("/tankers/fleet-vehicles")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<List<FleetVehicleOption>>> fleetVehicles() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(transport.fleetVehicles(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/tankers")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<TankerResponse>> createTanker(@Valid @RequestBody TankerRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tanker added", transport.createTanker(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/tankers/{id}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<TankerResponse>> updateTanker(@PathVariable UUID id, @Valid @RequestBody TankerRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Tanker updated", transport.updateTanker(TenantContext.getTenantIdAsObject(), id, req.registration(), req.name(), req.fleetVehicleId(), Boolean.TRUE.equals(req.unlinkFleetVehicle()))));
    }

    @DeleteMapping("/tankers/{id}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deactivateTanker(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        transport.deactivateTanker(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/drivers")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<DriverResponse>>> drivers() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(transport.drivers(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/deliveries/{id}/allocate")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<DeliveryResponse>> allocate(@PathVariable UUID id, @Valid @RequestBody AllocationRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Load allocated", transport.allocate(TenantContext.getTenantIdAsObject(), id, req, null,
                new EligibilityDtos.ReviewAuthority(FuelEligibilityAuthority.canReview(), TenantContext.getCurrentUserId()))));
    }

    /** What allocating this tanker and driver would run into: Blocks, Reviews and information. Changes nothing. */
    @PostMapping("/deliveries/{id}/eligibility")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE','FUEL_READ')")
    public ResponseEntity<ApiResponse<EligibilityDtos.EligibilityResult>> eligibility(@PathVariable UUID id, @Valid @RequestBody AllocationRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(transport.previewEligibility(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @GetMapping("/tankers/{id}/compartment-loads")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<za.co.handyflow.platform.fuel.dto.CompartmentLoadView>>> compartmentLoads(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(transport.compartmentLoads(TenantContext.getTenantIdAsObject(), id,
                date != null ? date : LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg")))));
    }

    @GetMapping("/planning")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<PlanningResponse>> planning(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(transport.planning(TenantContext.getTenantIdAsObject(),
                date != null ? date : LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg")))));
    }

    // ── Driver app: only the signed-in driver's own loads ────────────────────

    @GetMapping("/driver/loads")
    @PreAuthorize("hasAnyAuthority('FUEL_DRIVER','FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<List<DeliveryResponse>>> myLoads() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(transport.myLoads(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/driver/loads/{id}/dispatch")
    @PreAuthorize("hasAnyAuthority('FUEL_DRIVER','FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<DeliveryResponse>> myDispatch(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("On the road", transport.myDispatch(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id)));
    }

    @PostMapping("/driver/loads/{id}/complete")
    @PreAuthorize("hasAnyAuthority('FUEL_DRIVER','FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<DeliveryResponse>> myComplete(@PathVariable UUID id, @Valid @RequestBody CompleteDeliveryRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Delivery recorded", transport.myComplete(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, req)));
    }

    // ── N3: the driver's arrival eligibility check ───────────────────────────

    @PostMapping("/driver/loads/{id}/arrival-check")
    @PreAuthorize("hasAnyAuthority('FUEL_DRIVER','FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.fuel.dto.ArrivalCheckDtos.ArrivalCheckView>> myArrivalCheck(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Checked", arrivalChecks.check(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id)));
    }

    @GetMapping("/driver/loads/{id}/arrival-check")
    @PreAuthorize("hasAnyAuthority('FUEL_DRIVER','FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.fuel.dto.ArrivalCheckDtos.ArrivalCheckView>> myLatestArrivalCheck(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(arrivalChecks.latest(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id).orElse(null)));
    }

    // ── C04: delivery windows and failed attempts ────────────────────────────

    @GetMapping("/deliveries/{id}/exceptions")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<ExceptionView>>> exceptions(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(exceptionService.list(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/deliveries/{id}/window")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> setWindow(@PathVariable UUID id, @RequestBody WindowRequest req) {
        featureGuard.requireModule("fuel");
        exceptionService.setWindow(TenantContext.getTenantIdAsObject(), id, req);
        return ResponseEntity.ok(ApiResponse.success("Window saved", null));
    }

    @PostMapping("/deliveries/{id}/failed-attempt")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<ExceptionView>> failedAttempt(@PathVariable UUID id, @Valid @RequestBody FailedAttemptRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Failed attempt recorded",
                exceptionService.recordAsOffice(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/driver/loads/{id}/failed-attempt")
    @PreAuthorize("hasAnyAuthority('FUEL_DRIVER','FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<ExceptionView>> myFailedAttempt(@PathVariable UUID id, @Valid @RequestBody FailedAttemptRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Failed attempt recorded",
                exceptionService.recordAsDriver(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), id, req)));
    }
}
