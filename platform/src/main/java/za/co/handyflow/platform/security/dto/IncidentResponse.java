package za.co.handyflow.platform.security.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID       id,
        UUID       siteId,
        String     siteName,
        UUID       shiftId,
        UUID       guardId,
        String     guardName,
        String     title,
        String     description,
        String     severity,
        String     status,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant    acknowledgedAt,
        Instant    resolvedAt,
        Instant    reportedAt,
        Instant    updatedAt
) {}