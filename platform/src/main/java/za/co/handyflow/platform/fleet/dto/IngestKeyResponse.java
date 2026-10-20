package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.UUID;

public record IngestKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        Instant createdAt,
        Instant lastUsedAt,
        Instant revokedAt,
        boolean active
) {}