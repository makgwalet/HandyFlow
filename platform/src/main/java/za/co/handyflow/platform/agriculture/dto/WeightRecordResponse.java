package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WeightRecordResponse(
        UUID id,
        UUID animalId,
        UUID groupId,
        LocalDate recordedDate,
        BigDecimal weightKg,
        Integer sampleSize,
        UUID recordedBy,
        String recordedByName,
        String notes,
        Instant createdAt
) {}
