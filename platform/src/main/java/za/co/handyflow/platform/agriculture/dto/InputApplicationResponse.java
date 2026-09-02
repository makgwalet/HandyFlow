package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InputApplicationResponse(
        UUID id,
        UUID cropCycleId,
        LocalDate applicationDate,
        String inputType,
        UUID inventoryItemId,
        String productUsed,
        BigDecimal quantityApplied,
        String unitOfMeasure,
        String applicationMethod,
        UUID appliedBy,
        String appliedByName,
        BigDecimal laborHours,
        BigDecimal cost,
        String weatherConditions,
        String notes,
        Instant createdAt
) {}
