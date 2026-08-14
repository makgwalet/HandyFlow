package za.co.handyflow.platform.bookingagency.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordBookAgencyPaymentRequest(
        @NotNull BigDecimal amount,
        @NotNull LocalDate paidDate,
        String method,
        String reference
) {}