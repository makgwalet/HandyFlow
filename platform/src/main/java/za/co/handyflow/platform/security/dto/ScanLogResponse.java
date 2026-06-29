package za.co.handyflow.platform.security.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ScanLogResponse(
        UUID       id,
        UUID       checkpointId,
        String     checkpointName,
        UUID       guardId,
        UUID       shiftId,
        Instant    scannedAt,
        BigDecimal latitude,
        BigDecimal longitude,
        String     scanType
) {}
