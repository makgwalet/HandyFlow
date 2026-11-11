package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderCancelRequest(
        @NotBlank(message = "A reason is required")
        @Size(min = 5, max = 300, message = "Reason must be 5 to 300 characters") String reason
) {}
