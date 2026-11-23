package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;

public record UpdateInventoryItemRequest(
        String itemName,
        BigDecimal reorderLevel,
        BigDecimal unitCost,
        String supplier,
        String notes,
        BigDecimal packSizeKg   // replaces the stored value: leaving it out clears it
) {
    public UpdateInventoryItemRequest(String itemName, BigDecimal reorderLevel, BigDecimal unitCost, String supplier, String notes) {
        this(itemName, reorderLevel, unitCost, supplier, notes, null);
    }
}
