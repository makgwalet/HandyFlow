package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateRotationAssignmentRequest(
        @NotNull UUID guardId,
        @NotNull UUID patternId,
        @NotNull LocalDate startsAt,
        @Min(0) int positionInCycle     // 0 = start of cycle
) {}
