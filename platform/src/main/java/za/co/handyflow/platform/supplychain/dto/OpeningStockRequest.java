package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OpeningStockRequest(
        UUID locationId,
        UUID catalogueItemId,
        BigDecimal qty,
        BigDecimal unitCost,
        BigDecimal reorderPoint,
        BigDecimal reorderQty,
        String binLocation
) {}
