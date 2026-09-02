package za.co.handyflow.platform.warehousing.dto;

import java.time.LocalDate;
import java.util.UUID;

public record InboundShipmentResponse(
        UUID id, UUID clientId, String referenceNumber, LocalDate expectedDate, LocalDate receivedDate,
        String status, String notes
) {}
