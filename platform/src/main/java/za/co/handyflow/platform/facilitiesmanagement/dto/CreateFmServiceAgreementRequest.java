package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFmServiceAgreementRequest(
        @NotNull UUID clientId, String billingType, BigDecimal monthlyFee, BigDecimal hourlyRate,
        @NotNull LocalDate startDate, LocalDate endDate
) {}
