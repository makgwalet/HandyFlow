package za.co.handyflow.platform.payrollbureau.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordPayFeeNotePaymentRequest(
        @NotNull BigDecimal amount,
        @NotNull LocalDate paidDate,
        String method,
        String reference
) {}