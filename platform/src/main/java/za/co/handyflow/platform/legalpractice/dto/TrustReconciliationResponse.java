package za.co.handyflow.platform.legalpractice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Figures are worked out live while the reconciliation is a draft or submitted ({@code live} true) and are the frozen ones once it
 * is approved. {@code changedSinceSubmitted} is true when a submitted reconciliation no longer matches the ledger.
 */
public record TrustReconciliationResponse(UUID id, LocalDate periodEnd, String status, BigDecimal statementBalance, String statementNote,
                                          BigDecimal cashBookBalance, BigDecimal clientLedgerTotal, BigDecimal outstandingDeposits,
                                          BigDecimal outstandingPayments, BigDecimal adjustedBankBalance, BigDecimal difference, boolean balanced,
                                          boolean live, boolean changedSinceSubmitted, int ledgerMismatchCount,
                                          List<Item> items, List<ClientAmount> negativeClients, List<Mismatch> ledgerMismatches,
                                          String preparedByName, Instant preparedAt, Instant submittedAt, String returnReason, String returnedByName,
                                          String approvedByName, Instant approvedAt, String approvalNote, boolean selfApproved) {

    public record Item(UUID id, String kind, BigDecimal amount, LocalDate itemDate, String description, String reference, UUID trustTransactionId) {}
    public record ClientAmount(UUID clientId, String clientName, BigDecimal amount) {}
    public record Mismatch(UUID clientId, String clientName, BigDecimal clientRecord, BigDecimal ledger) {}
}
