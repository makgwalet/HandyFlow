package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the fuel manager needs to see first thing: stock, orders, money not yet billed, holds, expiring paperwork, loads with no truck. */
public record DashboardResponse(
        LocalDate today,
        List<StockByType> stock,
        int lowTanks,
        long quotes, long confirmedOrders, long partDeliveredOrders,
        long deliveredNotInvoicedCount, BigDecimal deliveredNotInvoicedValue, long invoiceFailures,
        long customersOnCreditHold,
        long complianceExpired, long complianceExpiring,
        long loadsToday, long loadsTodayUnallocated,
        long purchaseOrdersAwaitingDelivery
) {
    public record StockByType(String fuelType, BigDecimal litresOnHand, BigDecimal litresReserved, BigDecimal litresAvailable, BigDecimal capacityLitres) {}
}
