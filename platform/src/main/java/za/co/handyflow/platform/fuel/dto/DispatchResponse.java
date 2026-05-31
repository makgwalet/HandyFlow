package za.co.handyflow.platform.fuel.dto;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record DispatchResponse(
        UUID id, UUID tankId, UUID vehicleId, UUID assetId, UUID customerId,
        String recipientName, BigDecimal litresDispensed, BigDecimal pricePerLitre,
        Instant dispatchedAt, Integer odometerReading, BigDecimal hoursReading,
        String authorisedBy, BigDecimal levelBefore, BigDecimal levelAfter,
        Instant createdAt
) {}