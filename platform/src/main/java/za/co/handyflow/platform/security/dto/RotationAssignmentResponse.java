package za.co.handyflow.platform.security.dto;

import java.time.LocalDate;
import java.util.UUID;

public record RotationAssignmentResponse(
        UUID id,
        UUID guardId,
        String guardName,
        UUID patternId,
        String patternName,
        LocalDate startsAt,
        LocalDate endsAt,
        int positionInCycle,
        boolean active
) {}
