package za.co.handyflow.platform.accounting.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Generic 4-column CSV: Date, Description, Reference, Amount (with a
 * header row). Amount is signed — positive = money in (CREDIT), negative
 * = money out (DEBIT) — the service translates this into the stored
 * (always-positive amount + transactionType) shape AccBankTransaction
 * actually uses internally.
 * <p>
 * Not tied to any specific SA bank's raw export format — those vary
 * (FNB/Standard Bank/Nedbank/Absa all differ). Staff may need to
 * reformat a real bank export to match these 4 columns before uploading.
 */
public record ImportBankTransactionsRequest(
        @NotBlank String csvBase64
) {}