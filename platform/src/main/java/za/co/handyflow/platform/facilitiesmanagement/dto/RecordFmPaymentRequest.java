package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Only {@code amount} is required (existing clients keep working). paymentDate defaults to today and cannot be in the future; method: EFT, CASH, CARD, CHEQUE or OTHER. */
public record RecordFmPaymentRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount, LocalDate paymentDate, String method, String reference
) {}