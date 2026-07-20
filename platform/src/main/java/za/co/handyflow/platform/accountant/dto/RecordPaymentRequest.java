package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordPaymentRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull LocalDate paymentDate,
        @NotBlank String paymentMethod,   // EFT, CASH, CARD, DEBIT_ORDER, OTHER
        String reference,
        String notes
) {
}