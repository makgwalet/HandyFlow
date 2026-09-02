package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record GenerateInvoiceRequest(
        @NotNull LocalDate periodEnd
) {}
