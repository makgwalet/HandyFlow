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
        String supplier
) {}
