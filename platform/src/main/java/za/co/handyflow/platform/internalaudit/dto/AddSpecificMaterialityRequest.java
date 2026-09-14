package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AddSpecificMaterialityRequest(
        @NotBlank String accountOrGlSegment,
        @NotNull BigDecimal threshold
) {}
