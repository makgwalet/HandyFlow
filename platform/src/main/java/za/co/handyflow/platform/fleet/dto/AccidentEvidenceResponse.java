package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.UUID;

public record AccidentEvidenceResponse(
        UUID id,
        UUID accidentId,
        String type,
        String description,
        String fileName,
        String contentType,
        long sizeBytes,
        Instant uploadedAt
) {}
