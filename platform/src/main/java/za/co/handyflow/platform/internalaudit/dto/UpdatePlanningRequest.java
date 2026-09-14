package za.co.handyflow.platform.internalaudit.dto;

import java.math.BigDecimal;

public record UpdatePlanningRequest(
        String objectives,
        String scope,
        String auditCriteria,
        BigDecimal overallMateriality,
        BigDecimal performanceMateriality,
        BigDecimal clearlyTrivialThreshold
) {}
