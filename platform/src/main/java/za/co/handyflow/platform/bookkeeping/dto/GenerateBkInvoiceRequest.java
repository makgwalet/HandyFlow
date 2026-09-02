package za.co.handyflow.platform.bookkeeping.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record GenerateBkInvoiceRequest(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd) {}
