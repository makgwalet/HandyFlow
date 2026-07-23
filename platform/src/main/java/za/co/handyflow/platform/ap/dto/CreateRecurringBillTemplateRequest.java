package za.co.handyflow.platform.ap.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateRecurringBillTemplateRequest(
        UUID       supplierId,
        @NotBlank  String supplierName,
        String     category,
        @NotBlank  String description,
        @NotNull @Positive BigDecimal amount,
        BigDecimal vatAmount,
        @Min(1) @Max(28) int dayOfMonth,
        Integer    leadDays,
        String     notes
) {}