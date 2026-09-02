package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateLitigationMatterRequest(
        @NotBlank String title,
        @NotBlank String opposingParty,
        String ourSide,
        BigDecimal estimatedExposure,
        String legalRepresentative,
        String courtOrForum,
        String caseReference,
        LocalDate nextKeyDate,
        String description
) {}
