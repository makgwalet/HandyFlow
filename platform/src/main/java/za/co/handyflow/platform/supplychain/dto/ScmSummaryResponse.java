package za.co.handyflow.platform.supplychain.dto;

public record ScmSummaryResponse(
        long totalSuppliers,
        long openPurchaseOrders,
        long pendingInvoices,
        long invoicesForApproval,
        long lowStockItems,
        long overdueInvoices
) {}
