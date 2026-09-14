package za.co.handyflow.platform.internalaudit.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditTestResponse(
        UUID id, UUID sampleItemId, String procedure, String result, String notes,
        UUID testedBy, Instant testedAt, List<AuditExceptionResponse> exceptions
) {}
