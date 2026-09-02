package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateGroupRequest(
        @NotNull UUID farmId,
        UUID productionAreaId,
        UUID enterpriseId,
        @NotNull UUID speciesId,
        @NotBlank String batchNumber,
        String breed,
        @NotNull Integer initialCount,
        @NotNull LocalDate originDate,
        @NotBlank String acquisitionType
) {}
