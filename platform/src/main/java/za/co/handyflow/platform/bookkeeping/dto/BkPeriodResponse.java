package za.co.handyflow.platform.bookkeeping.dto;

import java.time.Instant;
import java.util.UUID;

public record BkPeriodResponse(
        UUID id, UUID clientId, int periodYear, int periodMonth, String status,
        Instant closedAt, UUID closedBy, Instant createdAt
) {}
