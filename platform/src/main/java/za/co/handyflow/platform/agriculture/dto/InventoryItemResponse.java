package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryItemResponse(
        UUID id,
        UUID farmId,
        String itemName,
        String category,
        String unitOfMeasure,
        BigDecimal currentQuantity,
        BigDecimal reorderLevel,
        BigDecimal unitCost,
        String supplier,
        String status,
        boolean belowReorderLevel,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
