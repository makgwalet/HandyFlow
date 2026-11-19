package za.co.handyflow.platform.marketing.dto;

import java.time.Instant;
import java.util.UUID;

public record SuppressionResponse(
        UUID    id,
        String  email,
        String  channel,
        String  reason,
        String  source,
        String  note,
        boolean active,
        Instant createdAt,
        Instant liftedAt,
        String  liftReason
) {}