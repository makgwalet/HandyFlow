package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id, UUID itemId, String movementType, BigDecimal quantity, BigDecimal unitCost, BigDecimal balanceAfter,
        UUID workOrderId, UUID costLineId, UUID purchaseOrderId, String reference, UUID createdByUserId, Instant createdAt, UUID storeId
) {}
