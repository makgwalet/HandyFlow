package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        String notes
) {}

