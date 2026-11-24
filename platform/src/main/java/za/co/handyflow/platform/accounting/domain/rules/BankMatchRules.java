package za.co.handyflow.platform.accounting.domain.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * When a bank statement line may be matched to a journal line, and when a match may be undone (audit finding 1.7).
 * A wrong match removes both items from the "unmatched" lists, so the reconciliation shows no difference and can be signed
 * off with the error inside it. Pure.
 */
public final class BankMatchRules {

    private BankMatchRules() {}

    /** The journal line a person chose, as the database holds it. {@code found} is false when no such line exists for this company. */
    public record Candidate(boolean found, String entryStatus, UUID accountId, BigDecimal debit, BigDecimal credit) {
        public static Candidate missing() { return new Candidate(false, null, null, null, null); }
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /**
     * @param bankGlAccountId  the ledger account the bank account is linked to (null when not linked)
     * @param moneyIn          true for a credit on the statement (money received): the matching line is a debit to the bank
     * @param amount           the statement line's amount
     * @param transactionDate  the statement line's date
     * @param signedOffThrough the date this bank account is signed off through, or null
     */
    public static String matchProblem(UUID bankGlAccountId, boolean moneyIn, BigDecimal amount, LocalDate transactionDate,
                                      LocalDate signedOffThrough, Candidate line) {
        if (bankGlAccountId == null)
            return "This bank account is not linked to a ledger account, so nothing can be matched to it";
        if (signedOffThrough != null && !transactionDate.isAfter(signedOffThrough))
            return "This statement line falls in a period that is already signed off (through " + signedOffThrough
                    + "). Reopen the reconciliation first";
        if (line == null || !line.found()) return "That journal line was not found";
        if (!"POSTED".equals(line.entryStatus()))
            return "Only a posted journal can be matched. That one is " + (line.entryStatus() == null ? "not posted" : line.entryStatus().toLowerCase());
        if (!bankGlAccountId.equals(line.accountId()))
            return "That journal line is not on this bank account's ledger account";
        BigDecimal onBankSide = moneyIn ? nz(line.debit()) : nz(line.credit());
        BigDecimal otherSide = moneyIn ? nz(line.credit()) : nz(line.debit());
        if (onBankSide.signum() <= 0 || otherSide.signum() != 0)
            return moneyIn ? "Money received must match a debit to the bank account, and that line is not one"
                           : "Money paid out must match a credit to the bank account, and that line is not one";
        if (onBankSide.compareTo(nz(amount)) != 0)
            return "The amounts differ: the statement says " + nz(amount).toPlainString() + " and the journal line " + onBankSide.toPlainString();
        return null;
    }

    /**
     * @param reconciled       whether the statement line is matched now
     * @param kind             MATCHED, NEW_JOURNAL or RULE
     * @param signedOffThrough as above
     */
    public static String unmatchProblem(boolean reconciled, String kind, LocalDate transactionDate, LocalDate signedOffThrough) {
        if (!reconciled) return "This statement line is not matched";
        if (signedOffThrough != null && !transactionDate.isAfter(signedOffThrough))
            return "This statement line falls in a period that is already signed off (through " + signedOffThrough
                    + "). Reopen the reconciliation first";
        if (!"MATCHED".equals(kind))
            return "This line was matched by creating a journal for it. Reverse that journal instead of un-matching the line";
        return null;
    }
}
