package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectPhase;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PhaseResponse(
        UUID id,
        UUID      projectId,
        String    name,
        String    description,
        int       sortOrder,
        String    status,
        LocalDate startDate,
        LocalDate endDate,
        Instant createdAt
) {
    public static PhaseResponse of(ProjectPhase p) {
        return new PhaseResponse(p.getId(), p.getProjectId(), p.getName(), p.getDescription(),
                p.getSortOrder(), p.getStatus(), p.getStartDate(), p.getEndDate(), p.getCreatedAt());
    }
}