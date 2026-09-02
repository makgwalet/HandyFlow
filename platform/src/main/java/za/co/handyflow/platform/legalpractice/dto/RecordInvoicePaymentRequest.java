package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecordInvoicePaymentRequest(@NotNull BigDecimal amount) {}
