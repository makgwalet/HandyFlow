package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record DipReadingResponse(
        UUID id, UUID tankId, Instant readAt,
        BigDecimal actualLitres, BigDecimal calculatedLitres,
        BigDecimal varianceLitres, boolean negativeVariance,
        String readBy, Instant createdAt
) {}