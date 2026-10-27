package za.co.handyflow.platform.facilities.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CorrectiveActionResponse(
        UUID id, String sourceType, UUID sourceId, UUID siteId, UUID assetId, String description, String priority,
        boolean critical, String status, LocalDate dueDate, boolean overdue, UUID workOrderId, String closureNote,
        Instant closedAt, Instant createdAt
) {}
