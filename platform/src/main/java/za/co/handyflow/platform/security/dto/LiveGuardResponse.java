package za.co.handyflow.platform.security.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A guard on an active shift, with the last known position and last checkpoint scan, for Live Operations. */
public record LiveGuardResponse(
        UUID guardId, String guardName, String grade,
        UUID shiftId, Instant shiftStart, Instant shiftEnd, boolean overrunning,
        UUID siteId, String siteName,
        BigDecimal latitude, BigDecimal longitude, Instant recordedAt, String gpsState,
        Instant lastScanAt, String lastScanCheckpoint
) {}
