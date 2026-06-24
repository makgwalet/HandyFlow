package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LogTimeRequest(
        UUID        taskId,
        LocalDate   entryDate,     // defaults to today
        BigDecimal  hours,         // required
        String      description,
        BigDecimal  latitude,
        BigDecimal  longitude
) {}
