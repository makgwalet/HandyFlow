package za.co.handyflow.platform.complianceservices.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClientComplianceDeadlineResponse(
        UUID id, UUID clientId, UUID registrationId, String deadlineType, String description,
        LocalDate dueDate, String status, boolean dueSoon, Instant completedAt
) {}
