package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record GenerateInvoiceRequest(@NotNull LocalDate periodEnd) {}
