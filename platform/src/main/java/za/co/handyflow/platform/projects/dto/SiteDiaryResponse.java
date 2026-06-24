package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.SiteDiary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SiteDiaryResponse(
        UUID        id,
        UUID        projectId,
        LocalDate   diaryDate,
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
        String      equipmentNotes,
        String      submittedByName,
        Instant     createdAt
) {
    public static SiteDiaryResponse of(SiteDiary d) {
        return new SiteDiaryResponse(
                d.getId(), d.getProjectId(), d.getDiaryDate(), d.getWeather(), d.getTempCelsius(),
                d.getWorkersPresent(), d.getWorkersPlanned(), d.getWorkDescription(),
                d.getProgressNotes(), d.getIssues(), d.getVisitorNames(), d.getIncidents(),
                d.getToolboxTopic(), d.getEquipmentNotes(), d.getSubmittedByName(), d.getCreatedAt());
    }
}
