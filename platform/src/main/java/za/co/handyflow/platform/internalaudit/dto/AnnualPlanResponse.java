package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnnualPlanResponse(
        UUID id, int planYear, String status,
        UUID approvedBy, Instant approvedAt, UUID createdBy, Instant createdAt,
        List<PlanEntryResponse> entries
) {}
