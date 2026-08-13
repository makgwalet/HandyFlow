package za.co.handyflow.platform.recruitmentagency.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordAgencyPaymentRequest(
        @NotNull BigDecimal amount,
        @NotNull LocalDate paidDate,
        String method,
        String reference
) {}