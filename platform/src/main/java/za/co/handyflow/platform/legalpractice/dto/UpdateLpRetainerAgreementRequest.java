package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateLpRetainerAgreementRequest(
        @NotNull BigDecimal monthlyFee,
        LocalDate endDate,
        String notes
) {}
