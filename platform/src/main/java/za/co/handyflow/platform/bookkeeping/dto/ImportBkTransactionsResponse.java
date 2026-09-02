package za.co.handyflow.platform.bookkeeping.dto;

/** Mirrors {@code accounting.ImportBankTransactionsResponse}'s own shape. */
public record ImportBkTransactionsResponse(int imported, int skippedDuplicates, int totalRows) {}
