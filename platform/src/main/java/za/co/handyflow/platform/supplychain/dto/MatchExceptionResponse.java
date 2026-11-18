package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import za.co.handyflow.platform.supplychain.domain.enums.MatchExceptionType;
import za.co.handyflow.platform.supplychain.domain.enums.MatchStatus;

public record MatchExceptionResponse(
        UUID id,
        MatchExceptionType type,
        MatchStatus resultingStatus,
        UUID poLineId,
        UUID invoiceLineId,
        String message,
        BigDecimal expectedValue,
        BigDecimal actualValue,
        String status,
        Instant createdAt,
        Instant resolvedAt
) {}
