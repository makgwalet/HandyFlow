package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectRisk;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RiskResponse(
        UUID id,
        UUID        projectId,
        String      riskNumber,
        String      title,
        String      description,
        String      category,
        int         probability,
        int         impact,
        int         riskScore,
        String      rating,
        String      status,
        String      mitigation,
        String      ownerName,
        LocalDate reviewDate,
        boolean     isOhsa,
        Instant createdAt,
        Instant     updatedAt
) {
    public static RiskResponse of(ProjectRisk r) {
        return new RiskResponse(
                r.getId(), r.getProjectId(), r.getRiskNumber(), r.getTitle(),
                r.getDescription(), r.getCategory(), r.getProbability(), r.getImpact(),
                r.getProbability() * r.getImpact(),   // risk_score is DB-generated, compute here
                r.getRating(), r.getStatus(), r.getMitigation(), r.getOwnerName(),
                r.getReviewDate(), r.isOhsa(), r.getCreatedAt(), r.getUpdatedAt());
    }
}