// fuel/dto/DeliveryResponse.java

package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal; import java.time.Instant;
import java.util.Map; import java.util.UUID;

public record DeliveryResponse(
        UUID id, UUID tankId, UUID customerId,
        Map<String, String> deliveryAddress, String fuelType,
        BigDecimal litresOrdered, BigDecimal litresDelivered,
        BigDecimal pricePerLitre, BigDecimal totalAmount,
        String status, Instant scheduledAt, Instant deliveredAt,
        String driverName, String vehicleReg,
        String receiverName, String receiverIdBadge,
        BigDecimal meterReadingStart, BigDecimal meterReadingEnd,
        String receiptNumber, Instant receiptGeneratedAt,
        boolean signedOnBehalf, String onBehalfOf,
        Instant createdAt
) {}