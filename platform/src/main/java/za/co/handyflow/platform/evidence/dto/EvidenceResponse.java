package za.co.handyflow.platform.evidence.dto;

import java.time.Instant;
import java.util.UUID;

public record EvidenceResponse(
        UUID id,
        String fileName,
        String contentType,
        long fileSizeBytes,
        String evidenceType,
        String status,
        String uploadedByName,
        Instant createdAt
) {
}