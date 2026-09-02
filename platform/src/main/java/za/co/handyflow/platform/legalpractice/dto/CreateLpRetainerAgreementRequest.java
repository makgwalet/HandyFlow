package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLpRetainerAgreementRequest(
        @NotNull UUID clientId,
        @NotNull BigDecimal monthlyFee,
        LocalDate startDate,
        LocalDate endDate,
        String notes
) {}
