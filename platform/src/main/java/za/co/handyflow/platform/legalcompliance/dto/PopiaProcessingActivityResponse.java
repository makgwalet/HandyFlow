package za.co.handyflow.platform.legalcompliance.dto;

import za.co.handyflow.platform.legalcompliance.domain.model.DataCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.LawfulBasis;
import za.co.handyflow.platform.legalcompliance.domain.model.PopiaProcessingActivity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PopiaProcessingActivityResponse(
        UUID id,
        String activityName,
        DataCategory dataCategory,
        String purpose,
        LawfulBasis lawfulBasis,
        String responsibleDepartment,
        UUID responsibleUserId,
        String responsibleUserName,
        String retentionPeriodDescription,
        boolean crossBorderTransfer,
        String crossBorderDetails,
        String securityMeasures,
        LocalDate reviewDate,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static PopiaProcessingActivityResponse of(PopiaProcessingActivity a) {
        return new PopiaProcessingActivityResponse(
                a.getId(), a.getActivityName(), a.getDataCategory(), a.getPurpose(), a.getLawfulBasis(),
                a.getResponsibleDepartment(), a.getResponsibleUserId(), a.getResponsibleUserName(),
                a.getRetentionPeriodDescription(), a.isCrossBorderTransfer(), a.getCrossBorderDetails(),
                a.getSecurityMeasures(), a.getReviewDate(), a.isActive(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
