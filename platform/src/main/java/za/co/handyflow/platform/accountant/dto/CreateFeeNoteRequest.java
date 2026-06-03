package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Fee notes
public record CreateFeeNoteRequest(
        @NotNull UUID clientId,
        @NotNull LocalDate invoiceDate,
        @NotNull LocalDate dueDate,
        @NotNull List<UUID> timeEntryIds,    // time entries to bill
        BigDecimal fixedFee,                 // if fixed fee override
        boolean includeVat,
        String notes
) {
}
