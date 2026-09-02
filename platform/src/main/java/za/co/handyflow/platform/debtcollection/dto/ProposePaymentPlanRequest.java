package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlanFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProposePaymentPlanRequest(
        @NotNull @Positive BigDecimal totalAgreedAmount,
        @NotNull @Positive BigDecimal installmentAmount,
        @NotNull PaymentPlanFrequency frequency,
        LocalDate startDate,
        @NotNull @Positive Integer numberOfInstallments,
        String notes
) {}
