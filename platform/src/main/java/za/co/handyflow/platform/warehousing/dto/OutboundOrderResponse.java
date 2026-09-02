package za.co.handyflow.platform.warehousing.dto;

import java.time.LocalDate;
import java.util.UUID;

public record OutboundOrderResponse(
        UUID id, UUID clientId, String orderReference, String shipToName, String shipToAddress,
        LocalDate requestedShipDate, LocalDate shippedDate, String status, String carrier, String trackingNumber,
        String notes
) {}
