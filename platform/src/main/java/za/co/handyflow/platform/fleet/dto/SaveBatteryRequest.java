package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Register (POST) or edit (PUT, full replace) a battery. Warranty: give {@code warrantyExpiry}, or
 * {@code warrantyMonths} with a purchase date to have the expiry worked out (an explicit expiry wins).
 */
public record SaveBatteryRequest(
        @NotBlank @Size(max = 100) String serialNumber,
        @NotBlank @Size(max = 100) String brand,
        @Size(max = 100) String model,
        @Min(12) @Max(24) int nominalVoltage,              // 12 or 24
        @Positive Integer capacityAh,
        @Size(max = 255) String supplier,
        LocalDate purchaseDate,
        @PositiveOrZero BigDecimal purchasePrice,
        @Positive @Max(120) Integer warrantyMonths,
        LocalDate warrantyExpiry,
        String notes
) {}