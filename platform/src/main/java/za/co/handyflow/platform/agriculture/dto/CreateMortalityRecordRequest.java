package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateMortalityRecordRequest(
        UUID animalId,
        UUID groupId,
        @NotNull LocalDate mortalityDate,
        @NotNull Integer countLost,
        @NotBlank String causeCategory,
        String causeDetail,
        BigDecimal estimatedValueLoss,
        UUID reportedBy,
        String notes
) {}
