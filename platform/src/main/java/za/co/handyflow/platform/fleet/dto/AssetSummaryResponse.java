package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of the fleet-assets list. */
public record AssetSummaryResponse(
        UUID vehicleId,
        String registration,
        String make,
        String model,
        String assetClass,
        String status,                 // operational status
        String lifecycleStatus,
        BigDecimal engineHours,
        Integer currentOdometer,
        boolean dueForService,
        String siteName,
        String operatorName,
        UUID coupledToVehicleId
) {}