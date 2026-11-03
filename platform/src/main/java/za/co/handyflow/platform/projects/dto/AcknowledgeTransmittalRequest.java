package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcknowledgeTransmittalRequest(
        @NotBlank(message = "Who acknowledged receipt is required")
        @Size(max = 255, message = "Name must not exceed 255 characters") String acknowledgedBy
) {}