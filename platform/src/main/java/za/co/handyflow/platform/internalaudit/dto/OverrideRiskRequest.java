package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;

public record OverrideRiskRequest(
        @NotBlank String finalAuditRisk, // LOW | MEDIUM | HIGH | CRITICAL
        String reason                     // required at the service layer only when this genuinely differs from the system-calculated value
) {}
