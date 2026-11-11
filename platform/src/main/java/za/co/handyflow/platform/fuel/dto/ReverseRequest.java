package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reason is mandatory for every reversal: it is the audit trail. */
public record ReverseRequest(
        @NotBlank(message = "A reason is required")
        @Size(min = 5, max = 500, message = "Reason must be 5 to 500 characters")
        String reason
) {}
