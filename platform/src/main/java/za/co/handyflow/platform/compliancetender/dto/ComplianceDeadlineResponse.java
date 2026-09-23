package za.co.handyflow.platform.compliancetender.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ComplianceDeadlineResponse(
        UUID id, UUID registrationId, String deadlineType, String description,
        LocalDate dueDate, String status, boolean dueSoon, Instant completedAt
) {}
