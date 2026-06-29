package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSwapRequest(
        @NotNull UUID originalShiftId,
        UUID proposedGuardId,           // nullable = open request
        @Size(max = 500) String reason
) {}
