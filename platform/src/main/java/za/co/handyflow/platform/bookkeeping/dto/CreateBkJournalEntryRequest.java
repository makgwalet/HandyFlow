package za.co.handyflow.platform.bookkeeping.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Mirrors {@code accounting.CreateJournalEntryRequest}'s own shape, adding {@code clientId}. */
public record CreateBkJournalEntryRequest(
        @NotNull UUID clientId, @NotNull LocalDate entryDate, String description, String reference,
        String entryType, @NotEmpty @Valid List<JournalLineRequest> lines
) {
    public record JournalLineRequest(
            @NotNull UUID accountId, String description, BigDecimal debitAmount, BigDecimal creditAmount
    ) {}
}
