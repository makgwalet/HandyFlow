package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditExceptionResponse(
        UUID id, UUID auditTestId, String description, String severity, String status,
        UUID raisedBy, Instant raisedAt, String resolutionNotes
) {}
