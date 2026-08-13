package za.co.handyflow.platform.bookingagency.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BookAgencyClientResponse(
        UUID id,
        String tradingName,
        String businessType,
        String timezone,
        String contactName,
        String contactEmail,
        String contactPhone,
        LocalDate onboardedAt,
        String status,
        String notes,
        Instant createdAt
) {}