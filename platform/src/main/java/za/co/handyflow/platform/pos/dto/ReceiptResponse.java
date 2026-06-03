package za.co.handyflow.platform.pos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Structured receipt data returned by GET /pos/transactions/{id}/receipt.
 * The API caller (desktop app, mobile app, thermal printer adapter) is responsible
 * for rendering to paper/PDF/screen. A pre-rendered HTML version is also provided
 * for web/email use.
 */
public record ReceiptResponse(
        // Header
        String tenantName,
        String tenantAddress,
        String tenantPhone,
        String tenantVatNumber,   // null if not VAT-registered

        // Transaction
        String  transactionNumber,
        Instant createdAt,
        String  cashierName,
        String  customerName,

        // Lines
        List<ReceiptLineItem> items,

        // Totals
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal vatAmount,
        BigDecimal totalAmount,

        // Payment
        String     paymentMethod,
        BigDecimal amountTendered,
        BigDecimal changeGiven,
        String     paymentRef,

        // Footer
        String footerMessage,    // e.g. "Thank you for your business!"

        // Pre-rendered HTML for web/email clients
        String htmlReceipt
) {
    public record ReceiptLineItem(
            String     itemName,
            String     sku,
            BigDecimal qty,
            BigDecimal unitPrice,
            BigDecimal discountPct,
            BigDecimal vatRate,
            BigDecimal lineTotal
    ) {}
}
