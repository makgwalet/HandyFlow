package za.co.handyflow.platform.bookkeeping.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Mirrors {@code accounting.JournalEntryResponse}'s own shape, scoped by {@code clientId}/{@code periodId}. */
public record BkJournalEntryResponse(
        UUID id, UUID clientId, UUID periodId, String entryNumber, LocalDate entryDate, String description,
        String reference, String entryType, String status, BigDecimal totalDebit, BigDecimal totalCredit,
        UUID createdBy, Instant createdAt, Instant postedAt, List<JournalLineResponse> lines
) {
    public record JournalLineResponse(
            UUID id, UUID accountId, String description, BigDecimal debitAmount, BigDecimal creditAmount, int sortOrder
    ) {}
}
