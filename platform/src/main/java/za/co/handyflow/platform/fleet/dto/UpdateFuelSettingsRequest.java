package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Full replace of a vehicle's fuel-analytics settings. A null {@code expectedLitresPer100Km} removes
 * the manual override (the vehicle's own rolling baseline is used); a null tolerance reverts to the default 20%.
 */
public record UpdateFuelSettingsRequest(
        @Positive @DecimalMax("200.0") BigDecimal expectedLitresPer100Km,
        @Min(5) @Max(100) Integer tolerancePercent
) {}