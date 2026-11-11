package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MovementResponse(UUID id, UUID tankId, String movementType, BigDecimal litresDelta, BigDecimal balanceAfter,
                               String sourceType, UUID sourceId, String reason, UUID createdBy, Instant createdAt) {}
