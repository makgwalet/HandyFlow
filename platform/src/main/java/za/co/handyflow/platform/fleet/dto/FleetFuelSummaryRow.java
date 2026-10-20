package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One vehicle on the fleet fuel board. */
public record FleetFuelSummaryRow(
        UUID vehicleId,
        String registration,
        String make,
        String model,
        String fuelType,
        FuelPeriodSummary summary,
        Double latestBaselineLitresPer100Km,   // baseline used for the most recent assessed segment
        Double latestDeviationPercent          // that segment's deviation; positive = thirstier
) {}