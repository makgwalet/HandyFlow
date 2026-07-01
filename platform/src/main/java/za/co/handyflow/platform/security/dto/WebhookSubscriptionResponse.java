package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

public record WebhookSubscriptionResponse(
        UUID    id,
        String  name,
        String  endpointUrl,
        String  eventTypesJson,
        UUID    branchId,
        boolean active,
        int     failureCount,
        boolean suspended,
        Instant lastSuccessAt,
        Instant createdAt
) {}
