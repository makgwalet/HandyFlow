package za.co.handyflow.platform.agriculture.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SeasonResponse(
        UUID id,
        UUID farmId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
