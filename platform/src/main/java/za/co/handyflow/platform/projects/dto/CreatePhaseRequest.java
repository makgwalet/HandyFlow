package za.co.handyflow.platform.projects.dto;

import java.time.LocalDate;

public record CreatePhaseRequest(
        String    name,        // required
        String    description,
        int       sortOrder,
        LocalDate startDate,
        LocalDate endDate
) {}
