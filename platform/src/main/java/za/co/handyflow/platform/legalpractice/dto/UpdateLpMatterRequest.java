package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateLpMatterRequest(
        @NotNull UUID attorneyId,
        @NotBlank String matterName,
        String description,
        @NotBlank String billingType,
        BigDecimal fixedFeeAmount,
        String notes
) {}
