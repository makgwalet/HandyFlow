package za.co.handyflow.platform.projects.dto;

import java.util.UUID;

public record DependencyResponse(
        UUID   id,
        UUID   predecessorId,
        String predecessorNumber,
        UUID   successorId,
        String successorNumber,
        String type,
        int    lagDays
) {}