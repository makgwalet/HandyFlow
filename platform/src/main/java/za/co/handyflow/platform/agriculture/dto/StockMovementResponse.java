package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID inventoryItemId,
        String movementType,
        LocalDate movementDate,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal totalCost,
        String referenceType,
        UUID referenceId,
        UUID performedBy,
        String performedByName,
        String notes,
        Instant createdAt
) {}
