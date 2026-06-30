package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record CameraResponse(
        UUID    id,
        UUID    siteId,
        String  siteName,
        String  name,
        String  provider,
        String  connectionConfigJson,
        String  status,
        Instant lastEventAt,
        String  notes,
        Instant createdAt
) {}
