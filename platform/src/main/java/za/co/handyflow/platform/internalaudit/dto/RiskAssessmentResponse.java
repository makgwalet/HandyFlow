package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.util.UUID;

public record RiskAssessmentResponse(
        UUID id, UUID universeEntryId,
        int inherentRiskScore, int controlRiskScore, int historicalFindingsScore,
        int timeSinceLastAuditScore, int businessRegulatoryImpactScore,
        String systemCalculatedRisk, String finalAuditRisk,
        String overrideReason, UUID overrideApprovedBy,
        UUID assessedBy, Instant assessedAt
) {}
