package za.co.handyflow.platform.legalpractice.application.internal;

import java.math.BigDecimal;

/**
 * The arithmetic of a trust reconciliation. The bank statement balance, plus deposits the bank has not yet credited, minus
 * payments the bank has not yet debited, is the adjusted bank balance; it must equal the trust ledger at the same date.
 */
public record ReconciliationFigures(BigDecimal statementBalance, BigDecimal outstandingDeposits, BigDecimal outstandingPayments,
                                    BigDecimal cashBookBalance, BigDecimal clientLedgerTotal, BigDecimal adjustedBankBalance,
                                    BigDecimal difference) {

    public static ReconciliationFigures of(BigDecimal statement, BigDecimal deposits, BigDecimal payments, BigDecimal cashBook, BigDecimal clientTotal) {
        BigDecimal adjusted = statement.add(deposits).subtract(payments);
        return new ReconciliationFigures(statement, deposits, payments, cashBook, clientTotal, adjusted, cashBook.subtract(adjusted));
    }

    /** Nothing is left unexplained and the ledger's own two totals agree. */
    public boolean balanced() {
        return difference.signum() == 0 && cashBookBalance.compareTo(clientLedgerTotal) == 0;
    }
}
