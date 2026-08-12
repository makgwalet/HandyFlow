package za.co.handyflow.platform.payrollbureau.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreatePayRunRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotNull LocalDate payDate
) {}