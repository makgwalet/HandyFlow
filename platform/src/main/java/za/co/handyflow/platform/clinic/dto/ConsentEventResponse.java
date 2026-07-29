package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.UUID;

public record ConsentEventResponse(
        UUID id,
        String consentType,
        String action,
        String method,
        String capturedByName,
        String notes,
        Instant createdAt
) {}