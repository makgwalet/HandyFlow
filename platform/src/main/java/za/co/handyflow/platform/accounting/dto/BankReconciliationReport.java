package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Where a bank account stands against its statement as at a date (finance patch F12). Amounts are signed: money in or a
 * debit to the bank is positive. {@code difference} is what is left after the items only on one side are taken out.
 */
public record BankReconciliationReport(
        UUID bankAccountId, String accountName, LocalDate asAt,
        BigDecimal statementBalance, BigDecimal bookBalance,
        BigDecimal statementOnlyNet, BigDecimal bookOnlyNet,
        BigDecimal difference, boolean balanced,
        LocalDate signedOffThrough, String signOffBlocker,
        List<StatementItem> onStatementOnly,
        List<BookItem> inBooksOnly,
        List<ReviewItem> bookedFromLines
) {
    /** A statement line with no journal yet. */
    public record StatementItem(UUID transactionId, LocalDate date, String description, String reference,
                                BigDecimal amount, long ageDays) {}

    /** A ledger line on the bank account that is not on the statement (yet). */
    public record BookItem(UUID journalLineId, UUID journalEntryId, String entryNumber, LocalDate date,
                           String description, BigDecimal amount, long ageDays) {}

    /** A line booked by a new journal or a rule since the last sign-off: what the person signing checks. */
    public record ReviewItem(UUID transactionId, LocalDate date, String description, BigDecimal amount,
                             String kind, UUID ruleId, String ruleName, UUID reconciledBy) {}
}
