package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentId,
        BigDecimal amount,
        String reason,
        String status,              // REQUESTED | COMPLETED | FAILED
        String providerRef,
        String requestedByName,
        Instant requestedAt,
        Instant completedAt,
        String failureReason
) {}