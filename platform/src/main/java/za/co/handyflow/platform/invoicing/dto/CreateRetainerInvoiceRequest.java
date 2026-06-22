package za.co.handyflow.platform.invoicing.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateRetainerInvoiceRequest(
        UUID customerId,       // null for walk-ins

        @NotBlank @Size(max = 255)
        String title,

        @NotNull @DecimalMin("1")
        BigDecimal committedHours,

        @NotNull @DecimalMin("0.00")
        BigDecimal ratePerHour,

        @NotNull
        BigDecimal vatRate,    // applied to the committed hours block

        String notes,

        // Walk-in fields
        @Size(max = 255) String walkinClientName,
        String walkinClientEmail,
        @Size(max = 50)  String walkinClientPhone
) {}
