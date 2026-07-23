package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record JournalEntryResponse(
        UUID id,
        String entryNumber,
        LocalDate entryDate,
        String description,
        String reference,
        String entryType,
        String status,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        boolean balanced,
        List<JournalLineResponse> lines,
        UUID createdBy,
        Instant createdAt
) {
    public record JournalLineResponse(
            UUID id,
            UUID accountId,
            String accountCode,
            String accountName,
            String description,
            BigDecimal debitAmount,
            BigDecimal creditAmount
    ) {}
}