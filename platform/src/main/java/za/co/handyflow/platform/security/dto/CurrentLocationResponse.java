package za.co.handyflow.platform.security.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// FIX: the real "current locations" read endpoint the Live Map has
// been missing — security_guard_current_location's own table comment
// already specified exactly this shape and the staleness rule before
// this endpoint existed to consume it. stale is computed server-side
// against GuardLocationService.LIVENESS_THRESHOLD_MINUTES, not left for
// the frontend to reimplement the same 5-minute cutoff independently.
public record CurrentLocationResponse(
        UUID guardId, String guardName, UUID shiftId, UUID siteId,
        BigDecimal latitude, BigDecimal longitude, Instant recordedAt, boolean stale
) {}
