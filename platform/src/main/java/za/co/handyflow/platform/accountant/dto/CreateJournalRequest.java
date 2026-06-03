package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Journals
public record CreateJournalRequest(
        @NotNull UUID periodId,
        @NotBlank String reference,
        @NotBlank String description,
        @NotBlank String journalType,
        @NotNull  LocalDate journalDate,
        @NotNull  @Size(min = 2) List<JournalLineRequest> lines
) {}
