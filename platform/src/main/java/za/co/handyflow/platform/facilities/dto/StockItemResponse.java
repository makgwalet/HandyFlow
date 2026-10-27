package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StockItemResponse(
        UUID id, String sku, String name, String unit, String location, BigDecimal onHand, BigDecimal reorderLevel,
        BigDecimal reorderQty, BigDecimal unitCost, BigDecimal stockValue, boolean belowReorderLevel, boolean active
) {}
