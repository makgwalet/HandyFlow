package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateSupplierInvoiceRequest(
        UUID supplierId,
        UUID purchaseOrderId,
        UUID goodsReceiptId,
        String supplierInvoiceRef,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currency,
        BigDecimal subtotal,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        String notes,
        /** Only for an invoice with no PO: the budget it should count against once approved. */
        String department,
        String costCentre,
        String projectRef,
        /** Optional line detail; totals are computed server-side, never taken from the client. */
        List<InvoiceLineRequest> lines
) {
    public record InvoiceLineRequest(
            UUID poLineId,
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal vatRate
    ) {}
}

