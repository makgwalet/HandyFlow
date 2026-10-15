package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatusEventResponse(UUID id, UUID assetId, String fromStatus, String toStatus, String reason,
                                  UUID actorUserId, BigDecimal meterHours, Instant happenedAt, UUID workOrderId,
                                  String overrideReason) {}
