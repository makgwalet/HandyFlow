package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FileDeadlineRequest(
        @NotNull LocalDate filedDate,
        String sarsReference,
        BigDecimal filingAmount,
        String notes
) {
}
