package za.co.handyflow.platform.warehousing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OutboundOrderLineResponse(
        UUID id, UUID orderId, UUID itemId, UUID locationId, BigDecimal qtyOrdered, BigDecimal qtyPicked,
        String notes, boolean fullyPicked
) {}
