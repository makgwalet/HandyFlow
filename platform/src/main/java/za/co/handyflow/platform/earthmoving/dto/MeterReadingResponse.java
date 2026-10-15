package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MeterReadingResponse(UUID id, String meterType, BigDecimal reading, BigDecimal previousReading,
                                   String source, String reason, UUID actorUserId, Instant recordedAt) {}
