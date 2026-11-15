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
import za.co.handyflow.platform.fuel.application.internal.FuelRouteOptimiserService;
import za.co.handyflow.platform.fuel.application.internal.FuelRouteService;
import za.co.handyflow.platform.fuel.dto.RouteDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** C04 part 2: routes with several stops in order. */
@RestController
@RequestMapping("/api/v1/fuel/routes")
@RequiredArgsConstructor
@Tag(name = "Fuel Routes", description = "One tanker and driver carrying several loads in order")
public class FuelRouteController {

    private final FuelRouteService routes;
    private final FuelRouteOptimiserService optimiser;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<RouteView>>> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(routes.list(TenantContext.getTenantIdAsObject(), date)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<RouteView>> get(@PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(routes.get(TenantContext.getTenantIdAsObject(), id)));
    }

    /** Phase 8b: a suggested stop order. Nothing is saved; apply it with PUT /{id}/stop-order. Optional start point (the depot) as startLat and startLng. */
    @GetMapping("/{id}/suggested-order")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<SuggestedOrder>> suggestedOrder(@PathVariable UUID id,
            @RequestParam(required = false) java.math.BigDecimal startLat, @RequestParam(required = false) java.math.BigDecimal startLng) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(optimiser.suggest(TenantContext.getTenantIdAsObject(), id, startLat, startLng)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<RouteView>> create(@Valid @RequestBody CreateRouteRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Route created",
                routes.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/stops")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<RouteView>> addStop(@PathVariable UUID id, @Valid @RequestBody AddStopRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Stop added", routes.addStop(TenantContext.getTenantIdAsObject(), id, req,
                new za.co.handyflow.platform.fuel.dto.EligibilityDtos.ReviewAuthority(FuelEligibilityAuthority.canReview(), TenantContext.getCurrentUserId()))));
    }

    @DeleteMapping("/{id}/stops/{deliveryId}")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<RouteView>> removeStop(@PathVariable UUID id, @PathVariable UUID deliveryId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Stop removed", routes.removeStop(TenantContext.getTenantIdAsObject(), id, deliveryId)));
    }

    @PutMapping("/{id}/stop-order")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<RouteView>> reorder(@PathVariable UUID id, @Valid @RequestBody StopOrderRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Order saved", routes.reorder(TenantContext.getTenantIdAsObject(), id, req.deliveryIds())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<RouteView>> cancel(@PathVariable UUID id, @Valid @RequestBody CancelRouteRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Route cancelled", routes.cancel(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }
}
