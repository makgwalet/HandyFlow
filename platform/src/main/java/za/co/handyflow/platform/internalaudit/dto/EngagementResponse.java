package za.co.handyflow.platform.internalaudit.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EngagementResponse(
        UUID id, UUID planEntryId, UUID universeEntryId, String universeEntryName,
        String name, String status, LocalDate startDate, LocalDate endDate,
        UUID createdBy, Instant createdAt,
        List<EngagementAssignmentResponse> assignments,
        // Phase 2 — planning detail + materiality
        String objectives, String scope, String auditCriteria,
        BigDecimal overallMateriality, BigDecimal performanceMateriality, BigDecimal clearlyTrivialThreshold,
        List<SpecificMaterialityResponse> specificMateriality
) {}
