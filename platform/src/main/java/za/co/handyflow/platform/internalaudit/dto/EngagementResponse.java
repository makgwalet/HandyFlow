package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EngagementResponse(
        UUID id, UUID planEntryId, UUID universeEntryId, String universeEntryName,
        String name, String status, LocalDate startDate, LocalDate endDate,
        UUID createdBy, Instant createdAt,
        List<EngagementAssignmentResponse> assignments
) {}
