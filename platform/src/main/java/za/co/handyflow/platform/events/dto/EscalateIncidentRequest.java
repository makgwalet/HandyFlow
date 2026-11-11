package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** raiseSeverityTo is optional and can only raise severity, never lower it. */
public record EscalateIncidentRequest(
        @NotBlank @Size(max = 500) String reason,
        String raiseSeverityTo
) {}