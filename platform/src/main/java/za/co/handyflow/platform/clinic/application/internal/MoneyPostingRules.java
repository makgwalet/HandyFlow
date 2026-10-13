package za.co.handyflow.platform.clinic.application.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a claim money event posts to the general ledger (patch 0185). Pure: no Spring, no database.
 * <ul>
 *   <li>SCHEME_PAYMENT_RECEIVED: debit the bank account's ledger account, credit Accounts Receivable (1100).</li>
 *   <li>CLAIM_WRITTEN_OFF: debit Bad Debts (5230), credit Accounts Receivable (1100).</li>
 *   <li>SCHEME_PAYMENT_REVERSED: the opposite of a payment: debit Accounts Receivable (1100), credit the bank account's ledger account.</li>
 * </ul>
 * Credit notes and voids are not handled here: the claim service already reverses the revenue journal itself when they happen.
 */
final class MoneyPostingRules {

    static final String SCHEME_PAYMENT = "SCHEME_PAYMENT_RECEIVED";
    static final String WRITE_OFF = "CLAIM_WRITTEN_OFF";
    static final String PAYMENT_REVERSED = "SCHEME_PAYMENT_REVERSED";
    static final String AR_CODE = "1100";
    static final String BAD_DEBT_CODE = "5230";

    static final String PENDING = "PENDING";
    static final String POSTED = "POSTED";
    static final String FAILED = "FAILED";

    private MoneyPostingRules() {}

    record Line(UUID accountId, BigDecimal debit, BigDecimal credit) {}

    /** The event types this listener records; anything else is ignored. */
    static boolean handles(String type) { return SCHEME_PAYMENT.equals(type) || WRITE_OFF.equals(type) || PAYMENT_REVERSED.equals(type); }

    /** A scheme payment cannot post until someone chooses the bank account; a write-off needs nothing and is posted at once. */
    static boolean needsBank(String type) { return SCHEME_PAYMENT.equals(type) || PAYMENT_REVERSED.equals(type); }

    /** The same event always has the same key, so a redelivered event is recorded once. */
    static String eventKey(String type, UUID claimId, BigDecimal amount, Instant occurredOn) {
        return type + "|" + claimId + "|" + amount.stripTrailingZeros().toPlainString() + "|" + (occurredOn == null ? 0 : occurredOn.toEpochMilli());
    }

    /** Why this cannot be posted yet, or null when it can. {@code bankGl} is only needed for a scheme payment. */
    static String blocker(String type, BigDecimal amount, UUID arId, UUID badDebtId, UUID bankGl) {
        if (amount == null || amount.signum() <= 0) return "The amount must be more than zero.";
        if (arId == null) return "The chart of accounts has no Accounts Receivable account (" + AR_CODE + ").";
        if (WRITE_OFF.equals(type) && badDebtId == null) return "The chart of accounts has no Bad Debts account (" + BAD_DEBT_CODE + ").";
        if (needsBank(type) && bankGl == null) return (PAYMENT_REVERSED.equals(type) ? "Choose the bank account the money is returned from" : "Choose the bank account this payment was received into") + " (it must be linked to a ledger account).";
        return null;
    }

    /** The two lines of the journal; call only when {@link #blocker} is null. */
    static List<Line> lines(String type, BigDecimal amount, UUID arId, UUID badDebtId, UUID bankGl) {
        if (PAYMENT_REVERSED.equals(type)) return List.of(new Line(arId, amount, null), new Line(bankGl, null, amount));
        UUID debitAccount = SCHEME_PAYMENT.equals(type) ? bankGl : badDebtId;
        return List.of(new Line(debitAccount, amount, null), new Line(arId, null, amount));
    }

    static String description(String type, UUID claimId, String reference) {
        String what = SCHEME_PAYMENT.equals(type) ? "Scheme payment" : PAYMENT_REVERSED.equals(type) ? "Scheme payment reversed" : "Claim write-off";
        return what + " on claim " + claimId + (reference == null || reference.isBlank() ? "" : " (" + reference.trim() + ")");
    }
}
