package za.co.handyflow.platform.bookingagency.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record GenerateRetainerInvoiceRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotNull LocalDate invoiceDate,
        @NotNull LocalDate dueDate,
        boolean includeVat
) {}