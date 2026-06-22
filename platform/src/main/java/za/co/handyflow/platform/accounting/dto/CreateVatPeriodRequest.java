package za.co.handyflow.platform.accounting.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateVatPeriodRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd
) {}
