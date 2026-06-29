package za.co.handyflow.platform.security.dto;


import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RotationPatternResponse(
        UUID id,
        UUID siteId,
        String siteName,
        String name,
        String patternType,
        Map<String, Object> cycleDefinition,
        int shiftLengthHours,
        int assignedGuardCount,
        boolean active,
        Instant createdAt
) {}
