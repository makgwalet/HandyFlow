package za.co.handyflow.platform.recruitmentagency.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateAgencyInvoiceRequest(
        @NotNull LocalDate invoiceDate,
        @NotNull LocalDate dueDate,
        boolean includeVat
) {}