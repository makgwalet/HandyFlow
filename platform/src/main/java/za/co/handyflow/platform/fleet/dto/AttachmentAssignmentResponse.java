package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AttachmentAssignmentResponse(
        UUID vehicleId,
        String registration,
        Instant attachedAt,
        BigDecimal attachedEngineHours,
        Instant detachedAt,
        BigDecimal detachedEngineHours,
        BigDecimal hours                 // null while still attached
) {}