package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// timeSinceLastAuditScore is deliberately NOT here — it's calculated
// server-side from AuditUniverseEntry.timeSinceLastAuditScore(), the
// one input that's genuinely derivable from data rather than requiring
// the auditor's own judgment, per the agreed design.
public record CreateRiskAssessmentRequest(
        @NotNull @Min(1) @Max(5) Integer inherentRiskScore,
        @NotNull @Min(1) @Max(5) Integer controlRiskScore,
        @NotNull @Min(1) @Max(5) Integer historicalFindingsScore,
        @NotNull @Min(1) @Max(5) Integer businessRegulatoryImpactScore
) {}
