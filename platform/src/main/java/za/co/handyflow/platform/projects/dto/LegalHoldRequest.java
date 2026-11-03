package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** hold=true places a legal hold, hold=false lifts it. A reason is required either way (checked in the service). */
public record LegalHoldRequest(
        @NotNull(message = "hold is required") Boolean hold,
        @Size(max = 500, message = "Reason must not exceed 500 characters") String reason
) {}
