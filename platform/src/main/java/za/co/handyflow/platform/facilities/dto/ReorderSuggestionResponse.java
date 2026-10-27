package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** An item at or below its reorder level, net of what is already on open orders. */
public record ReorderSuggestionResponse(
        UUID itemId, String sku, String name, String unit, BigDecimal onHand, BigDecimal reorderLevel,
        BigDecimal onOrder, BigDecimal suggestedQuantity, BigDecimal unitCost
) {}
