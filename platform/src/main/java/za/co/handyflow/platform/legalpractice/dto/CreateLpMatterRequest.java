package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLpMatterRequest(
        @NotNull UUID clientId,
        @NotNull UUID attorneyId,
        @NotBlank String matterNumber,
        @NotBlank String matterType,
        @NotBlank String matterName,
        String description,
        @NotBlank String billingType,
        BigDecimal fixedFeeAmount,
        LocalDate openedDate,
        String notes
) {}
