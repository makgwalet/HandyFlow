package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.ClaimStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateAccidentClaimRequest(
        @NotNull ClaimStatus status,
        @Size(max = 255) String insurer,
        @Size(max = 100) String claimNumber,
        @DecimalMin("0.00") BigDecimal excess,
        @Size(max = 255) String assessorName,
        LocalDate assessmentDate,
        LocalDate submittedAt,
        @DecimalMin("0.00") BigDecimal settlementAmount,
        LocalDate settledAt
) {}
