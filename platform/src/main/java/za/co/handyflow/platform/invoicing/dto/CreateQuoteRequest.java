package za.co.handyflow.platform.invoicing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateQuoteRequest(
        // Either customerId OR walkinClientName must be provided
        // Validated in service layer, not here
        UUID customerId,

        @NotBlank(message = "Quote title is required")
        @Size(max = 255)
        String title,

        String notes,

        // Walk-in client fields — only used when customerId is null
        @Size(max = 255)
        String walkinClientName,

        @Email(message = "Invalid email format")
        String walkinClientEmail,

        @Size(max = 50)
        String walkinClientPhone
) {}
