// security/dto/EvidenceResponse.java
package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record EvidenceResponse(
        UUID id,
        String entityType,
        UUID entityId,
        String category,
        String fileUrl,
        String fileName,
        String notes,
        UUID uploadedBy,
        Instant createdAt
) {}