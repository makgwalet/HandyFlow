package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.UUID;

/** List-view summary of an inspection. */
public record InspectionResponse(
        UUID id,
        UUID vehicleId,
        String registration,
        UUID templateId,
        String templateName,
        String inspectionType,
        String inspectorName,
        Integer odometer,
        String status,
        String verdict,        // null until completed
        int failedCount,
        Instant startedAt,
        Instant completedAt
) {}