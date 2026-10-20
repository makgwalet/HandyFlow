package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** List / vehicle-view summary of a battery. */
public record BatteryResponse(
        UUID id,
        String serialNumber,
        String brand,
        String model,
        int nominalVoltage,
        Integer capacityAh,
        String status,
        UUID vehicleId,
        String registration,
        String position,
        LocalDate warrantyExpiry,
        boolean inWarranty,
        BigDecimal lastVoltage,
        String lastTestResult,         // GOOD | WEAK | FAILED; null if never tested
        Instant lastTestedAt,
        int daysInService              // lifetime, including the current installation
) {}