package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record ComplianceRequest(
        @NotBlank String itemType,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 80) String referenceNo,
        @NotBlank String subjectType,
        UUID subjectId,
        @Size(max = 120) String subjectLabel,   // used for COMPANY / OTHER subjects
        LocalDate issuedOn,
        @NotNull LocalDate expiresOn,
        @Size(max = 500) String notes
) {}
