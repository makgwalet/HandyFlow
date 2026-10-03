package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.agriculture.application.internal.AgEquipmentCostService;
import za.co.handyflow.platform.agriculture.application.internal.AgFuelCostService;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostEntryResponse;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.AllocateFuelRequest;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.CostEquipmentRequest;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.EquipmentOption;
import za.co.handyflow.platform.agriculture.dto.EquipmentFuelDtos.FuelOverview;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Costs equipment use and allocates own fuel into the cost ledger (ADR-001, W4). Everything needs AGRICULTURE_FINANCE, and the owning module's own
 * rule on top: equipment rates sit behind FLEET_READ and a tank's cost per litre behind FUEL_MARGIN_READ (Fuel restricts its cost and margin data
 * with it), so this API never shows a rate its owner would not show the same person.
 */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Equipment and fuel", description = "Cost machine use from Fleet and allocate own fuel from Fuel")
public class AgEquipmentFuelController {

    private final AgEquipmentCostService equipmentService;
    private final AgFuelCostService fuelService;
    private final FeatureGuard featureGuard;

    @GetMapping("/finance/equipment")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE') and hasAuthority('FLEET_READ')")
    @Operation(summary = "The tenant's machines with Fleet's operating rate per hour (service and repairs only; null until set in Fleet)")
    public ResponseEntity<ApiResponse<List<EquipmentOption>>> equipment() {
        featureGuard.requireModule("agriculture");
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(equipmentService.equipment(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/farms/{farmId}/equipment/cost")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE') and hasAuthority('FLEET_READ')")
    @Operation(summary = "Cost one day's use of a machine, split across the targets it worked on; the rate is snapshotted into the ledger")
    public ResponseEntity<ApiResponse<List<CostEntryResponse>>> costEquipment(@PathVariable UUID farmId, @Valid @RequestBody CostEquipmentRequest request) {
        featureGuard.requireModule("agriculture");
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                equipmentService.costUse(TenantContext.getTenantIdAsObject(), farmId, TenantContext.getCurrentUserId(), request)));
    }

    @GetMapping("/farms/{farmId}/fuel/unallocated")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE') and hasAuthority('FUEL_MARGIN_READ')")
    @Operation(summary = "Own-fuel dispatches (to the tenant's own vehicles and assets, never customer sales) not yet allocated; default the last 30 days")
    public ResponseEntity<ApiResponse<FuelOverview>> unallocatedFuel(@PathVariable UUID farmId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("agriculture");
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(fuelService.unallocated(TenantContext.getTenantIdAsObject(), farmId, from, to)));
    }

    @PostMapping("/farms/{farmId}/fuel/allocate")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE') and hasAuthority('FUEL_MARGIN_READ')")
    @Operation(summary = "Allocate one dispatch's whole cost across targets on the farm; the tank's cost per litre is snapshotted into the ledger")
    public ResponseEntity<ApiResponse<List<CostEntryResponse>>> allocateFuel(@PathVariable UUID farmId, @Valid @RequestBody AllocateFuelRequest request) {
        featureGuard.requireModule("agriculture");
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                fuelService.allocate(TenantContext.getTenantIdAsObject(), farmId, TenantContext.getCurrentUserId(), request)));
    }
}
