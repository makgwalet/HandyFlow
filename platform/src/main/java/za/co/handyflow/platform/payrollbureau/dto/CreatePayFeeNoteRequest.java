package za.co.handyflow.platform.payrollbureau.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePayFeeNoteRequest(
        @NotNull UUID payRunId,
        @NotNull LocalDate invoiceDate,
        @NotNull LocalDate dueDate,
        boolean includeVat
) {}