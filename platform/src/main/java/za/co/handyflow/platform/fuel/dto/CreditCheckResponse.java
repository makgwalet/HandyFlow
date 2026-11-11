package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;

/** Credit exposure if this order were confirmed. Zero credit limit means a cash customer: any exposure is over the limit. */
public record CreditCheckResponse(
        BigDecimal creditLimit, BigDecimal invoicesOutstanding, BigDecimal deliveredNotInvoiced,
        BigDecimal otherOpenOrders, BigDecimal thisOrder, BigDecimal exposureAfter,
        boolean withinLimit, boolean onCreditHold
) {}
