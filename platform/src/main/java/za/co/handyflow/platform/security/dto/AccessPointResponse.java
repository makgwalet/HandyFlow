package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record AccessPointResponse(
        UUID id, UUID siteId, String name, String description,
        boolean active, Instant createdAt
) {}