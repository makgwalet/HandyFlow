package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFeedRecordRequest(
        UUID animalId,
        UUID groupId,
        @NotNull LocalDate feedDate,
        UUID inventoryItemId,
        @NotBlank String feedType,
        @NotNull BigDecimal quantityKg,
        BigDecimal costPerKg,
        String notes
) {}
