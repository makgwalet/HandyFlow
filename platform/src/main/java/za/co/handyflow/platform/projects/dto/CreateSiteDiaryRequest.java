package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateSiteDiaryRequest(
        LocalDate   diaryDate,       // required
        String      weather,
        BigDecimal  tempCelsius,
        int         workersPresent,
        Integer     workersPlanned,
        String      workDescription,
        String      progressNotes,
        String      issues,
        String      visitorNames,
        String      incidents,
        String      toolboxTopic,
        String      equipmentNotes
) {}
