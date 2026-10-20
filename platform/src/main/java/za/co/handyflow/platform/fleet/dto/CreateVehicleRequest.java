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
import java.util.UUID;

public record CreateVehicleRequest(
        // ── Existing fields (unchanged) ───────────────────────────────────
        @NotBlank String registration,
        @NotBlank String make,
        @NotBlank String model,
        Integer year,
        String colour,
        @Size(max = 50) String vin,    // VIN / chassis number
        @NotBlank String vehicleType,
        String fuelType,
        LocalDate licenceDiscExpiry,
        LocalDate roadworthyExpiry,
        LocalDate insuranceExpiry,
        BigDecimal dailyRate,
        BigDecimal tankCapacityLitres,
        Integer serviceIntervalKm,     // custom interval — defaults to 10000
        Integer serviceIntervalDays,   // time-based interval — e.g. 180 days
        String assignedDriverName,
        String notes,
        UUID customerId,

        // ── NEW: identity ─────────────────────────────────────────────────
        @Size(max = 50) String engineNumber,
        @Size(max = 100) String variant,
        @Size(max = 50) String bodyType,
        Transmission transmission,
        DriveType driveType,
        @Min(1) @Max(20) Integer axleCount,
        @PositiveOrZero Integer gvmKg,
        @PositiveOrZero Integer tareKg,
        @PositiveOrZero Integer payloadKg,   // derived as gvm - tare when omitted
        @PositiveOrZero Integer gcmKg,

        // ── NEW: acquisition & valuation ──────────────────────────────────
        LocalDate acquisitionDate,
        AcquisitionMethod acquisitionMethod,
        @PositiveOrZero BigDecimal purchasePrice,
        @PositiveOrZero BigDecimal currentBookValue,
        @PositiveOrZero BigDecimal currentEstimatedValue,
        LocalDate valuedAt               // date the book/estimated values were assessed
) {}