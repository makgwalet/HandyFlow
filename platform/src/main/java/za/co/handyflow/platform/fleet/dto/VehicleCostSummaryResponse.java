package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cost-per-km rollup for one vehicle. All three cost/km figures are
 * computed the same way — see FleetCostService — so a fleet-wide list of
 * these can be sorted/compared directly against each other.
 */
public record VehicleCostSummaryResponse(
        UUID vehicleId,
        String registration,
        String make,
        String model,
        BigDecimal totalServiceCost,
        BigDecimal totalFuelCost,
        BigDecimal totalCost,
        int totalKm,
        BigDecimal costPerKm // null if totalKm is 0 — division by zero, not "free to run"
) {}
