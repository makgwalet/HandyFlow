package za.co.handyflow.platform.agriculture.dto;

import java.time.LocalDate;

public record UpdateScoutingRecordRequest(
        String severity,
        String description,
        String recommendedAction,
        LocalDate followUpDate,
        String notes
) {}
