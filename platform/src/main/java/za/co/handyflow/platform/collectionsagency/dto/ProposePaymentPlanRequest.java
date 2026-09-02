package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProposePaymentPlanRequest(
        @NotNull @Positive BigDecimal totalAgreedAmount, @NotNull @Positive BigDecimal installmentAmount,
        @NotBlank String frequency, LocalDate startDate, @NotNull @Positive Integer numberOfInstallments,
        String notes
) {}
