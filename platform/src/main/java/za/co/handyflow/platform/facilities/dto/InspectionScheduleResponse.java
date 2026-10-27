package za.co.handyflow.platform.facilities.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InspectionScheduleResponse(
        UUID id, UUID templateId, String templateName, UUID siteId, String siteName, UUID assetId, String assetName,
        int frequencyDays, LocalDate nextDueDate, LocalDate lastCompletedDate, boolean active, boolean overdue,
        long daysUntilDue, Instant createdAt
) {}
