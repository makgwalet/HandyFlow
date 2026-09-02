package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.legalcompliance.domain.model.DataCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.LawfulBasis;

import java.time.LocalDate;
import java.util.UUID;

public record UpdatePopiaActivityRequest(
        @NotBlank String activityName,
        @NotNull DataCategory dataCategory,
        String purpose,
        @NotNull LawfulBasis lawfulBasis,
        String responsibleDepartment,
        UUID responsibleUserId,
        String responsibleUserName,
        String retentionPeriodDescription,
        boolean crossBorderTransfer,
        String crossBorderDetails,
        String securityMeasures,
        LocalDate reviewDate
) {}
