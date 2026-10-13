package za.co.handyflow.platform.debtcollection.application.internal;

import java.math.BigDecimal;

/**
 * Splits a payment between fees and principal. Pure.
 * <ul>
 *   <li>explicitFees given: use it (must be between 0 and min(amount, fees owed)).</li>
 *   <li>else feesFirst=true: fees are settled first, up to what is owed.</li>
 *   <li>else (default): everything goes to principal.</li>
 * </ul>
 * The order is a business/legal choice, hence the configuration switch (LedgerProperties) rather than a fixed rule.
 * Principal is never capped here: invoicing owns the truth about principal, and a payment may be recorded here
 * before or after invoicing reflects it, so capping against its live balance would wrongly reject valid payments.
 */
final class PaymentAllocator {

    record Split(BigDecimal principal, BigDecimal fees) {}

    private PaymentAllocator() {}

    static Split split(BigDecimal amount, BigDecimal feesOutstanding, boolean feesFirst, BigDecimal explicitFees) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        BigDecimal owed = feesOutstanding == null ? BigDecimal.ZERO : feesOutstanding.max(BigDecimal.ZERO);
        BigDecimal fees;
        if (explicitFees != null) {
            if (explicitFees.signum() < 0 || explicitFees.compareTo(amount) > 0 || explicitFees.compareTo(owed) > 0) {
                throw new IllegalArgumentException("allocateToFees must be between 0 and the lesser of the payment and the fees owed (R" + owed + ")");
            }
            fees = explicitFees;
        } else if (feesFirst) {
            fees = amount.min(owed);
        } else {
            fees = BigDecimal.ZERO;
        }
        return new Split(amount.subtract(fees), fees);
    }
}
