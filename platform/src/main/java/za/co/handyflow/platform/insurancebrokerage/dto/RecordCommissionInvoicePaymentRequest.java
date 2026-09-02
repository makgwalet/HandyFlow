package za.co.handyflow.platform.insurancebrokerage.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RecordCommissionInvoicePaymentRequest(
        @NotNull @Positive BigDecimal amount
) {}
