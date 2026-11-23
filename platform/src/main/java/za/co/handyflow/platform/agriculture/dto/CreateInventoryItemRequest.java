package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateInventoryItemRequest(
        @NotNull UUID farmId,
        @NotBlank String itemName,
        @NotBlank String category,
        @NotBlank String unitOfMeasure,
        BigDecimal reorderLevel,
        BigDecimal unitCost,
        String supplier,
        BigDecimal packSizeKg   // kg in one bag, bale or sack; only for items counted in those units
) {
    public CreateInventoryItemRequest(UUID farmId, String itemName, String category, String unitOfMeasure,
                                      BigDecimal reorderLevel, BigDecimal unitCost, String supplier) {
        this(farmId, itemName, category, unitOfMeasure, reorderLevel, unitCost, supplier, null);
    }
}
