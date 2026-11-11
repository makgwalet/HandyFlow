package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveIncidentRequest(
        @NotBlank @Size(max = 1000) String resolution
) {}