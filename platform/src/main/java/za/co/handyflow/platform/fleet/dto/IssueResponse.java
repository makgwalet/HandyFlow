package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.UUID;

public record IssueResponse(
        UUID id,
        UUID vehicleId,
        String registration,
        String source,
        UUID sourceInspectionId,
        String title,
        String description,
        String severity,
        String status,
        Instant createdAt,
        Instant resolvedAt,
        String resolutionNote
) {}