package za.co.handyflow.platform.ap.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdateRecurringBillTemplateRequest(
        @NotBlank  String description,
        @NotNull @Positive BigDecimal amount,
        BigDecimal vatAmount,
        @Min(1) @Max(28) int dayOfMonth,
        Integer    leadDays,
        String     notes
) {}