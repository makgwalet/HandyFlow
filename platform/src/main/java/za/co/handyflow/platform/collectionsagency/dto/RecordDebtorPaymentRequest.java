package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordDebtorPaymentRequest(
        @NotNull @Positive BigDecimal amount, LocalDate transactionDate, String reference, String notes
) {}
