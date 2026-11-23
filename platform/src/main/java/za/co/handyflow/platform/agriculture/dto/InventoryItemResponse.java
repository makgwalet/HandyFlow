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
        Instant updatedAt,
        UUID supplierId,       // the item's usual Supply Chain supplier, if one is set
        String supplierName,   // its name (null if none, or if it was removed from Supply Chain)
        BigDecimal packSizeKg, // kg in one bag/bale/sack; null for other units or when not set
        BigDecimal costPerKg   // price per bag / kg per bag (or the unit cost converted, for a mass unit); null when not known
) {
    /** Before pack sizes: the same record without the last two fields. */
    public InventoryItemResponse(UUID id, UUID farmId, String itemName, String category, String unitOfMeasure,
                                 BigDecimal currentQuantity, BigDecimal reorderLevel, BigDecimal unitCost, String supplier,
                                 String status, boolean belowReorderLevel, String notes, Instant createdAt, Instant updatedAt,
                                 UUID supplierId, String supplierName) {
        this(id, farmId, itemName, category, unitOfMeasure, currentQuantity, reorderLevel, unitCost, supplier, status,
                belowReorderLevel, notes, createdAt, updatedAt, supplierId, supplierName, null, null);
    }
}
