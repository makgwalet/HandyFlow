package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Three-way match for one purchase order: ordered value, received value (from goods receipts) and invoiced value
 * (disputed invoices excluded, VAT excluded). {@code variance} = invoiced - received; the order matches when it is
 * within {@code tolerance}.
 */
public record PurchaseOrderMatchResponse(
        UUID purchaseOrderId, String poNumber, BigDecimal orderedValue, BigDecimal receivedValue, BigDecimal invoicedValue,
        BigDecimal variance, BigDecimal tolerance, String status, List<VendorInvoiceResponse> invoices
) {}
