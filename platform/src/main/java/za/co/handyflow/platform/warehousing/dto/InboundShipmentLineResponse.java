package za.co.handyflow.platform.warehousing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InboundShipmentLineResponse(
        UUID id, UUID shipmentId, UUID itemId, BigDecimal expectedQty, BigDecimal receivedQty, UUID locationId,
        String notes, BigDecimal outstandingQty
) {}
