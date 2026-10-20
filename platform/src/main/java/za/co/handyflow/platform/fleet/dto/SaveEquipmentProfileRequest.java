package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.AssetClass;

import java.math.BigDecimal;

/**
 * Configure (or reconfigure) a vehicle as a fleet asset. Full replace of these settings; the hour meters
 * are recorded separately (see RecordMetersRequest). {@code lastServiceHours} may be omitted: when hour-based
 * servicing is first set up it defaults to the asset's current engine hours, so it isn't due on day one.
 */
public record SaveEquipmentProfileRequest(
        @NotNull AssetClass assetClass,
        @PositiveOrZero BigDecimal operatingRatePerHour,
        @DecimalMin("0.5") @DecimalMax("24.0") BigDecimal targetHoursPerDay,   // defaults to 8
        @Positive Integer serviceIntervalHours,
        @PositiveOrZero BigDecimal lastServiceHours,
        @Size(max = 150) String siteName,
        @Size(max = 50) String axleConfiguration,
        @Size(max = 255) String operatorCompetency,
        String notes
) {}