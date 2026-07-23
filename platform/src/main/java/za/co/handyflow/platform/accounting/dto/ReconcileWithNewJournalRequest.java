package za.co.handyflow.platform.accounting.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Categorizes a bank transaction that has no existing journal entry to
 * match against — the common case for most real transactions on a first
 * import (bank fees, direct debits, interest, anything never entered
 * anywhere else). The bank's own GL account is one side automatically
 * (debit if money in, credit if money out — mirroring the transaction's
 * direction); otherAccountId is the other side staff picks (an expense,
 * income, or whatever account actually explains the movement).
 */
public record ReconcileWithNewJournalRequest(
        @NotNull UUID otherAccountId,
        String description
) {}