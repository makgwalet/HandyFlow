package za.co.handyflow.platform.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateJournalEntryRequest(
        @NotNull LocalDate entryDate,
        @NotBlank String description,
        String reference,
        String entryType,
        @NotEmpty List<JournalLineRequest> lines
) {
    public record JournalLineRequest(
            @NotNull UUID accountId,
            String description,
            BigDecimal debitAmount,
            BigDecimal creditAmount
    ) {}
}