package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.AcquisitionMethod;
import za.co.handyflow.platform.fleet.domain.model.DriveType;
import za.co.handyflow.platform.fleet.domain.model.Transmission;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Full-replace edit of a vehicle's descriptive details. Not included on purpose:
 * status (own endpoint + transition rules), odometer / engine hours (floor-guarded
 * write paths), and driver assignment (own endpoint).
 */
public record UpdateVehicleRequest(
        @NotBlank String registration,
        @NotBlank String make,
        @NotBlank String model,
        Integer year,
        String colour,
        @NotBlank String vehicleType,
        String fuelType,
        LocalDate licenceDiscExpiry,
        LocalDate roadworthyExpiry,
        LocalDate insuranceExpiry,
        BigDecimal dailyRate,
        BigDecimal tankCapacityLitres,
        Integer serviceIntervalKm,
        Integer serviceIntervalDays,
        String notes,

        @Size(max = 50) String vin,
        @Size(max = 50) String engineNumber,
        @Size(max = 100) String variant,
        @Size(max = 50) String bodyType,
        Transmission transmission,
        DriveType driveType,
        @Min(1) @Max(20) Integer axleCount,
        @PositiveOrZero Integer gvmKg,
        @PositiveOrZero Integer tareKg,
        @PositiveOrZero Integer payloadKg,
        @PositiveOrZero Integer gcmKg,

        LocalDate acquisitionDate,
        AcquisitionMethod acquisitionMethod,
        @PositiveOrZero BigDecimal purchasePrice,
        @PositiveOrZero BigDecimal currentBookValue,
        @PositiveOrZero BigDecimal currentEstimatedValue,
        LocalDate valuedAt
) {}