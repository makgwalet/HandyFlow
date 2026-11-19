package za.co.handyflow.platform.marketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LiftSuppressionRequest(
        @NotBlank @Size(min = 10, max = 1000,
                message = "A documented reason of at least 10 characters is required") String reason
) {}