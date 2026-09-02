package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;

public record UpdateInventoryItemRequest(
        String itemName,
        BigDecimal reorderLevel,
        BigDecimal unitCost,
        String supplier,
        String notes
) {}
