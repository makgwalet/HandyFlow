package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Flags the downstream effect of an RFI's answer. Clear both flags to say there is none. */
public record RfiImpactRequest(
        UUID taskId,
        boolean impactsSchedule,
        boolean impactsCost,
        @Size(max = 4000, message = "Notes must not exceed 4 000 characters") String notes
) {}