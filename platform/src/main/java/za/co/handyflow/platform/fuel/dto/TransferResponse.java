package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID id, UUID fromTankId, UUID toTankId, BigDecimal litres, String reason, Instant createdAt) {}
