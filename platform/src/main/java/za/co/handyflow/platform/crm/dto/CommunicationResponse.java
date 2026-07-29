package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.util.UUID;

public record CommunicationResponse(
        UUID    id,
        UUID    customerId,
        String  type,
        String  direction,
        String  summary,
        Instant occurredAt,
        UUID    loggedBy,
        Instant createdAt
) {}