package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stock on hand valued at each item's current average cost, as at {@code asOf} (now: this is not a historical report).
 * Items that are inactive but still hold stock are included. {@code lines} is one row per store and item.
 */
public record StockValuationResponse(Instant asOf, BigDecimal totalValue, List<StoreTotal> byStore, List<Line> lines) {

    public record StoreTotal(UUID storeId, String storeCode, String storeName, long itemLines, BigDecimal value) {}

    public record Line(UUID storeId, String storeCode, UUID itemId, String sku, String name, String unit, BigDecimal onHand,
                       BigDecimal unitCost, BigDecimal value, boolean itemActive) {}
}
