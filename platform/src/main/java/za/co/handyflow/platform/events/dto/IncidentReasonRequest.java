package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Used when reopening an incident — a reopened incident should always say why. */
public record IncidentReasonRequest(
        @NotBlank @Size(max = 500) String reason
) {}