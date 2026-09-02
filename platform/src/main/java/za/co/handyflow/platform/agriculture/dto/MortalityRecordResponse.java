package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MortalityRecordResponse(
        UUID id,
        UUID animalId,
        UUID groupId,
        LocalDate mortalityDate,
        Integer countLost,
        String causeCategory,
        String causeDetail,
        BigDecimal estimatedValueLoss,
        UUID reportedBy,
        String reportedByName,
        String notes,
        Instant createdAt
) {}
