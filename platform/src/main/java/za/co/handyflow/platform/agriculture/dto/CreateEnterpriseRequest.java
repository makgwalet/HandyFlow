package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateEnterpriseRequest(
        @NotNull UUID farmId,
        @NotBlank String name,
        @NotBlank String enterpriseType,
        String speciesFocus,
        LocalDate startDate
) {}
