package za.co.handyflow.platform.fuel.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record DispatchFuelRequest(
        @NotNull BigDecimal litresDispensed,
        BigDecimal pricePerLitre,
        @NotNull Instant dispatchedAt,
        UUID vehicleId, UUID assetId, UUID customerId,
        String recipientName, Integer odometerReading,
        BigDecimal hoursReading, String authorisedBy, String notes
) {}
