package za.co.handyflow.platform.facilities.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PpmScheduleResponse(
        UUID id, UUID assetId, String taskName, String description, Integer frequencyDays,
        LocalDate nextDueDate, LocalDate lastCompletedDate, boolean active, Instant createdAt
) {}
