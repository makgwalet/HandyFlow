package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.legalcompliance.domain.model.LitigationMatterType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLitigationMatterRequest(
        @NotBlank String title,
        @NotNull LitigationMatterType matterType,
        @NotBlank String opposingParty,
        String ourSide,
        BigDecimal estimatedExposure,
        String legalRepresentative,
        String courtOrForum,
        String caseReference,
        @NotNull LocalDate openedDate,
        LocalDate nextKeyDate,
        String description,
        UUID linkedContractId
) {}
