package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RecordInvoicePaymentRequest(@NotNull @Positive BigDecimal amount) {}
