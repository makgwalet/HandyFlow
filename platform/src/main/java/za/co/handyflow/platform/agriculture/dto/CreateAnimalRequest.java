package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateAnimalRequest(
        @NotNull UUID farmId,
        UUID productionAreaId,
        UUID enterpriseId,
        @NotNull UUID speciesId,
        @NotBlank String tagNumber,
        String name,
        String breed,
        @NotBlank String sex,
        LocalDate dateOfBirth,
        boolean estimatedAge,
        UUID sireId,
        UUID damId,
        @NotBlank String acquisitionType,
        @NotNull LocalDate acquisitionDate,
        BigDecimal acquisitionCost
) {}
