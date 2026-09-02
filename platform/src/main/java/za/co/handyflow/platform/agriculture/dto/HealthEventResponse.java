package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HealthEventResponse(
        UUID id,
        UUID animalId,
        UUID groupId,
        String eventType,
        LocalDate eventDate,
        String description,
        String productUsed,
        String dosage,
        UUID administeredBy,
        String administeredByName,
        String veterinarian,
        BigDecimal cost,
        Integer withdrawalPeriodDays,
        LocalDate nextDueDate,
        boolean reminderAcknowledged,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
