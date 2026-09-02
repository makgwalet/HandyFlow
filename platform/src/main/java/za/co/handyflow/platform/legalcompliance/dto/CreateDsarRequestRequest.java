package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.legalcompliance.domain.model.DataCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.DsarRequestType;

import java.time.LocalDate;

public record CreateDsarRequestRequest(
        @NotNull DsarRequestType requestType,
        @NotNull DataCategory dataCategory,
        @NotBlank String requesterName,
        String requesterEmail,
        String requesterContact,
        @NotNull LocalDate receivedDate
) {}
