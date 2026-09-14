package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.util.UUID;

public record PlanEntryResponse(
        UUID id, UUID universeEntryId, String universeEntryName,
        UUID riskAssessmentId, String riskLevel,
        Integer plannedQuarter, String rationale, String status, Instant createdAt
) {}
