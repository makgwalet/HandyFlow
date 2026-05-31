package za.co.handyflow.platform.invoicing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordPaymentRequest(
        @NotNull @Positive BigDecimal amountPaid,
        LocalDate paidDate,           // null = today
        String paymentMethod,         // EFT, CASH, CARD — informational only
        String reference              // bank reference, cheque number, etc.
) {}