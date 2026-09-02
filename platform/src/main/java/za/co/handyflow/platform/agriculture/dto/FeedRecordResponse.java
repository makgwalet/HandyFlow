package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FeedRecordResponse(
        UUID id,
        UUID animalId,
        UUID groupId,
        LocalDate feedDate,
        UUID inventoryItemId,
        String feedType,
        BigDecimal quantityKg,
        BigDecimal costPerKg,
        BigDecimal totalCost,
        String notes,
        Instant createdAt
) {}
