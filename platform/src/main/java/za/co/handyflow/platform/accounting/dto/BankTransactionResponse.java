package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// RECONSTRUCTED, NOT COPIED FROM SOURCE: this file wasn't available
// directly, but AccountingService's toBankTransactionResponse() call
// (new BankTransactionResponse(t.getId(), t.getBankAccountId(),
// t.getTransactionDate(), t.getDescription(), t.getReference(),
// t.getAmount(), t.getTransactionType(), t.getBalanceAfter(),
// t.isReconciled(), t.getCreatedAt())) fixes the first 10 fields' exact
// order — Java positional records must match call order, so this isn't a
// guess for those. journalLineId and reconciledAt are genuinely new,
// added here for the reconciliation feature.
public record BankTransactionResponse(
        UUID       id,
        UUID       bankAccountId,
        LocalDate  transactionDate,
        String     description,
        String     reference,
        BigDecimal amount,
        String     transactionType,
        BigDecimal balanceAfter,
        boolean    reconciled,
        Instant    createdAt,
        UUID       journalLineId,
        Instant    reconciledAt
) {}