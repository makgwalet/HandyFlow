package za.co.handyflow.platform.fuel.dto;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record DispatchResponse(
        UUID id, UUID tankId, UUID vehicleId, UUID assetId, UUID customerId,
        String recipientName, BigDecimal litresDispensed, BigDecimal pricePerLitre,
        Instant dispatchedAt, Integer odometerReading, BigDecimal hoursReading,
        String authorisedBy, BigDecimal levelBefore, BigDecimal levelAfter,
        Instant createdAt,
        Instant reversedAt, String reversalReason,
        UUID destinationDepotId,
        UUID transferId
) {
    public DispatchResponse(UUID id, UUID tankId, UUID vehicleId, UUID assetId, UUID customerId, String recipientName, BigDecimal litresDispensed, BigDecimal pricePerLitre,
                            Instant dispatchedAt, Integer odometerReading, BigDecimal hoursReading, String authorisedBy, BigDecimal levelBefore, BigDecimal levelAfter,
                            Instant createdAt, Instant reversedAt, String reversalReason, UUID destinationDepotId) {
        this(id, tankId, vehicleId, assetId, customerId, recipientName, litresDispensed, pricePerLitre, dispatchedAt, odometerReading, hoursReading, authorisedBy,
                levelBefore, levelAfter, createdAt, reversedAt, reversalReason, destinationDepotId, null);
    }
    public DispatchResponse(UUID id, UUID tankId, UUID vehicleId, UUID assetId, UUID customerId, String recipientName, BigDecimal litresDispensed, BigDecimal pricePerLitre,
                            Instant dispatchedAt, Integer odometerReading, BigDecimal hoursReading, String authorisedBy, BigDecimal levelBefore, BigDecimal levelAfter,
                            Instant createdAt, Instant reversedAt, String reversalReason) {
        this(id, tankId, vehicleId, assetId, customerId, recipientName, litresDispensed, pricePerLitre, dispatchedAt, odometerReading, hoursReading, authorisedBy,
                levelBefore, levelAfter, createdAt, reversedAt, reversalReason, null, null);
    }
}