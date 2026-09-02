package za.co.handyflow.platform.agriculture.dto;

import java.time.LocalDate;

public record UpdateSeasonRequest(
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String notes
) {}
