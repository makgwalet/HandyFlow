package za.co.handyflow.platform.bookingagency.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record BookingResponse(
        UUID id, String bookingNumber, UUID clientId,
        UUID resourceId, String resourceName, UUID offeringId, String offeringName,
        String customerName, String customerPhone, String customerEmail,
        LocalDateTime startDatetime, LocalDateTime endDatetime,
        String status, String notes, Instant createdAt
) {}