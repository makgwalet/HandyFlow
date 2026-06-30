package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record DetailAssignmentResponse(
        UUID    id,
        UUID    detailId,
        UUID    guardId,
        String  guardName,
        String  role,
        Instant assignmentStart,
        Instant assignmentEnd,
        boolean active
) {}
