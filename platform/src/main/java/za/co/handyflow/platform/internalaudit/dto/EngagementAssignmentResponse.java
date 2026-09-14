package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.util.UUID;

public record EngagementAssignmentResponse(
        UUID id, UUID userId, String userName, String role, UUID assignedBy, Instant assignedAt
) {}
