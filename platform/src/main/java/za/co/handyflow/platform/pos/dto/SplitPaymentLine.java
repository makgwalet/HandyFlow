package za.co.handyflow.platform.pos.dto;

import java.math.BigDecimal;

/**
 * One leg of a split payment. Used inside ProcessSaleRequest when paymentMethod = SPLIT.
 * Example: R200 cash + R150 card.
 *
 * Validation rule (enforced in PosService):
 *   sum(splitPayments[].amount) must equal totalAmount.
 */
public record SplitPaymentLine(
        String     paymentMethod,   // CASH | CARD | EFT | ACCOUNT | VOUCHER
        BigDecimal amount,
        BigDecimal amountTendered,  // only relevant for CASH leg
        String     paymentRef       // EFT ref / card auth for non-cash legs
) {}
