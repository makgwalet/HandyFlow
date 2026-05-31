package za.co.handyflow.platform.marketing.dto;

import java.time.Instant;
import java.util.UUID;

public record ContactPreferenceResponse(
        UUID    id,
        String  email,
        String  name,
        String  entityType,
        UUID    entityId,
        boolean emailOptedIn,
        Instant emailOptedInAt,
        Instant emailOptedOutAt,
        String  optInSource,
        Instant createdAt
) {}
