package za.co.handyflow.platform.agriculture.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ScoutingRecordResponse(
        UUID id,
        UUID cropCycleId,
        LocalDate scoutingDate,
        String observationType,
        String severity,
        String description,
        String recommendedAction,
        UUID scoutedBy,
        String scoutedByName,
        LocalDate followUpDate,
        boolean followUpAcknowledged,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}
