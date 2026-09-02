package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record GenerateFmInvoiceRequest(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd) {}
