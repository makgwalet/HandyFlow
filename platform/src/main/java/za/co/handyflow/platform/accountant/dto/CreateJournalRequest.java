package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

// Journals
// FIX: periodId replaced with periodYear/periodMonth — there was no
// way for any caller to obtain a valid periodId, since no period-
// creation or period-listing endpoint existed anywhere. No working
// caller of this endpoint existed before this change (no Create
// Journal UI has ever existed), so this is a clean break, not a
// breaking change to real usage. AccountantService.createJournal() now
// resolves-or-creates the period from these two fields.
public record CreateJournalRequest(
        @NotNull @Min(2020) Integer periodYear,
        @NotNull @Min(1) @Max(12) Integer periodMonth,
        @NotBlank String reference,
        @NotBlank String description,
        @NotBlank String journalType,
        @NotNull  LocalDate journalDate,
        @NotNull  @Size(min = 2) List<JournalLineRequest> lines
) {}