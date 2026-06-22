package za.co.handyflow.platform.accounting.dto;

import java.time.LocalDate;

public record ReverseJournalRequest(
        LocalDate reversalDate   // optional — defaults to today if null
) {}