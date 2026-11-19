package za.co.handyflow.platform.marketing.dto;

import java.time.Instant;
import java.util.UUID;

public record DeliveryEventResponse(
        UUID    id,
        String  provider,
        String  eventType,
        String  email,
        Instant occurredAt,
        String  outcome,
        String  detail,
        Instant createdAt
) {}