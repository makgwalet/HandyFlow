package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(UUID id, UUID tankId, BigDecimal litres, UUID customerId, String referenceType,
                                  UUID referenceId, String status, Instant expiresAt, String note,
                                  Instant createdAt, Instant closedAt) {}
