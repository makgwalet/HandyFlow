package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** On create, openingQuantity (with openingUnitCost) books opening stock as a receipt. SKU cannot change after creation. */
public record UpsertStockItemRequest(
        @NotBlank @Size(max = 60) String sku, @NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 20) String unit,
        @Size(max = 100) String location, @DecimalMin("0") BigDecimal reorderLevel, @DecimalMin("0") BigDecimal reorderQty,
        @DecimalMin("0") BigDecimal openingQuantity, @DecimalMin("0") BigDecimal openingUnitCost
) {}
