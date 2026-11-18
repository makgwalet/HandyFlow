package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One PO revision. {@code snapshot} is only filled when a single revision is fetched. */
public record PoVersionResponse(
        UUID id,
        int revisionNumber,
        UUID amendmentId,
        String reason,
        BigDecimal subtotal,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        String createdByName,
        Instant createdAt,
        Map<String, Object> snapshot
) {}
