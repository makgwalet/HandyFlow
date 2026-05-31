package za.co.handyflow.platform.pos.dto;
import java.math.BigDecimal;
public record PosSummaryResponse(
        BigDecimal salesToday,
        BigDecimal salesThisMonth,
        long       transactionsToday,
        long       transactionsThisMonth,
        long       totalStockItems,
        long       lowStockItems,
        long       pendingOrders
) {}