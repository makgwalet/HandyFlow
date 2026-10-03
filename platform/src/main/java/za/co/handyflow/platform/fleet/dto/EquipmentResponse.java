package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** A vehicle seen as a machine that is run by the hour: its meter and what an hour of use costs to keep running (service and repairs only). */
public record EquipmentResponse(
        UUID id,
        String registration,
        String make,
        String model,
        String vehicleType,
        String status,
        BigDecimal engineHours,
        BigDecimal operatingRatePerHour
) {}
