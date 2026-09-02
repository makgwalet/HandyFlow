package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkOrderResponse(
        UUID id, String workOrderNumber, UUID siteId, UUID assetId, UUID ppmScheduleId,
        String category, String priority, String status, String description, String reportedBy,
        UUID technicianId, String technicianName, UUID vendorId, String vendorName,
        LocalDate scheduledDate, Instant completedAt, String completionNotes,
        BigDecimal cost, String cancellationReason, Instant createdAt
) {}
