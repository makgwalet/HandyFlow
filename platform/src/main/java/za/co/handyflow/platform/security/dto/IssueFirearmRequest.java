package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record IssueFirearmRequest(
        @NotNull UUID guardId,
        @NotNull UUID witnessedByGuardId,
        UUID   sessionId,    // optional — link to Phase 2 device session if applicable
        UUID   shiftId,      // optional — link to shift if applicable
        String conditionNotes
) {}
