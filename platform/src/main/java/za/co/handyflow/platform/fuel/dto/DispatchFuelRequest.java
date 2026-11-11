package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record DispatchFuelRequest(
        @NotNull @Positive(message = "Litres dispensed must be greater than zero") BigDecimal litresDispensed,
        @PositiveOrZero(message = "Price per litre cannot be negative") BigDecimal pricePerLitre,
        @NotNull Instant dispatchedAt,
        UUID vehicleId, UUID assetId, UUID customerId,
        String recipientName, Integer odometerReading,
        BigDecimal hoursReading, String authorisedBy, String notes
) {}
