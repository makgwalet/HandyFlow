package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id, String orderNumber, UUID accountId, UUID customerId, String customerName,
        UUID siteId, String siteName, String fuelType, BigDecimal litresOrdered, BigDecimal pricePerLitre,
        BigDecimal value, String status, LocalDate requestedDate, LocalDate quoteValidUntil,
        UUID tankId, String tankName, BigDecimal litresDelivered, BigDecimal litresScheduled, BigDecimal litresRemaining,
        String creditOverrideReason, String cancelReason, String customerReference, String notes,
        Instant createdAt, List<DeliveryResponse> deliveries
) {}
