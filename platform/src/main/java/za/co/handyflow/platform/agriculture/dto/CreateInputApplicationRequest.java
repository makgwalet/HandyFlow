package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateInputApplicationRequest(
        @NotNull LocalDate applicationDate,
        @NotBlank String inputType,
        UUID inventoryItemId,
        String productUsed,
        @NotNull BigDecimal quantityApplied,
        @NotBlank String unitOfMeasure,
        String applicationMethod,
        UUID appliedBy,
        BigDecimal laborHours,
        BigDecimal cost,
        String weatherConditions,
        String notes
) {}
