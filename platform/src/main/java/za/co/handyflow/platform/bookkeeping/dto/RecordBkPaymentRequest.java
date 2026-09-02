package za.co.handyflow.platform.bookkeeping.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecordBkPaymentRequest(@NotNull BigDecimal amount) {}
