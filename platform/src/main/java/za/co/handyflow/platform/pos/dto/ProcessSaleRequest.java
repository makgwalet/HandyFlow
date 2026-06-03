package za.co.handyflow.platform.pos.dto;

import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request body for POST /pos/sell.
 *
 * Payment routing:
 *  - Single payment: set paymentMethod + amountTendered (for CASH).
 *  - Split payment:  set paymentMethod = "SPLIT" and populate splitPayments list.
 *    The sum of splitPayments[].amount must equal the computed totalAmount.
 */
public record ProcessSaleRequest(
        UUID             customerId,
        String           customerName,
        @NotEmpty List<SaleLineItem> items,

        // Single payment
        String           paymentMethod,     // CASH|CARD|EFT|ACCOUNT|SPLIT|VOUCHER
        BigDecimal       amountTendered,    // only for CASH single payment
        String           paymentRef,        // EFT ref / card auth code

        // Split payment — populated only when paymentMethod = SPLIT
        List<SplitPaymentLine> splitPayments,

        // Optional discount at transaction level (e.g. loyalty discount)
        BigDecimal       transactionDiscountPct,    // applied after line discounts

        // Session link — populated automatically by service; clients need not set this
        UUID             cashSessionId,

        String           notes
) {
    public record SaleLineItem(
            UUID       catalogueItemId,
            String     itemName,           // fallback if no catalogueItemId (custom/open item)
            BigDecimal qty,
            BigDecimal unitPrice,          // override catalogue price if set
            BigDecimal discountPct         // line-level discount
    ) {}
}
