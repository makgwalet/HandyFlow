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
import za.co.handyflow.platform.fuel.application.internal.FuelRouteCostService;
import za.co.handyflow.platform.fuel.dto.RouteCostDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** F02: actual versus estimated cost of routes. Reads need the margin permission; recording needs the transport permission. */
@RestController
@RequestMapping("/api/v1/fuel/cost-actuals")
@RequiredArgsConstructor
@Tag(name = "Fuel Route Costs", description = "Actual versus estimated cost of routes")
public class FuelRouteCostController {

    private final FuelRouteCostService costs;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_MARGIN_READ')")
    public ResponseEntity<ApiResponse<RouteCostReport>> report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(costs.report(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/routes/{routeId}/entries")
    @PreAuthorize("hasAuthority('FUEL_MARGIN_READ')")
    public ResponseEntity<ApiResponse<List<CostEntryView>>> entries(@PathVariable UUID routeId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(costs.list(TenantContext.getTenantIdAsObject(), routeId)));
    }

    @PostMapping("/routes/{routeId}/entries")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<CostEntryView>> add(@PathVariable UUID routeId, @Valid @RequestBody CostEntryRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Cost recorded",
                costs.add(TenantContext.getTenantIdAsObject(), routeId, req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/entries/{entryId}/void")
    @PreAuthorize("hasAnyAuthority('FUEL_MANAGE','FUEL_TRANSPORT_MANAGE')")
    public ResponseEntity<ApiResponse<CostEntryView>> voidEntry(@PathVariable UUID entryId, @Valid @RequestBody VoidEntryRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Entry voided",
                costs.voidEntry(TenantContext.getTenantIdAsObject(), entryId, req.reason(), TenantContext.getCurrentUserId())));
    }
}
