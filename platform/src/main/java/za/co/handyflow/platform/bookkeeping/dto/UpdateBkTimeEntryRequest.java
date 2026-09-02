package za.co.handyflow.platform.bookkeeping.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateBkTimeEntryRequest(
        LocalDate entryDate, String activityType, String description,
        BigDecimal hours, BigDecimal hourlyRate, boolean billable
) {}
