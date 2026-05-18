package za.co.handyflow.platform.invoicing.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record AddLineItemRequest(
        // nullable — allows free-typed items
        UUID catalogueItemId,

        @NotBlank(message = "Description is required")
        String description,

        @NotBlank(message = "Unit is required")
        String unit,

        @NotNull @DecimalMin("0.01")
        BigDecimal quantity,

        @NotNull @DecimalMin("0.00")
        BigDecimal unitPrice,

        @DecimalMin("0.00") @DecimalMax("100.00")
        BigDecimal vatRate
) {}