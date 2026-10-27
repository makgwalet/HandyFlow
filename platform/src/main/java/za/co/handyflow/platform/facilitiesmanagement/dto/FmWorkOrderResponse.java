package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** STAFF-facing response. Never return this to portal users — see {@link FmPortalWorkOrderResponse}. */
public record FmWorkOrderResponse(
        UUID id, String workOrderNumber, UUID clientId, UUID siteId, UUID assetId, UUID ppmScheduleId,
        String category, String priority, String status, String description, String reportedBy,
        UUID technicianId, String technicianName, UUID vendorId, String vendorName,
        LocalDate scheduledDate, Instant completedAt, String completionNotes, String holdReason,
        BigDecimal cost, boolean invoiced, String cancellationReason, Instant createdAt,
        UUID requestedByUserId, boolean safetyImpact, UUID duplicateOfWorkOrderId, Instant approvedAt,
        String rejectionReason, boolean requiresVerification, Instant verifiedAt,
        int reworkCount, String lastReworkReason, Integer downtimeMinutes, String failureCause,
        BigDecimal internalCost,
        UUID agreementId, String coverage, Instant responseDueAt, Instant resolutionDueAt, Instant respondedAt,
        String responseSla, String resolutionSla, Instant slaPausedAt
) {}