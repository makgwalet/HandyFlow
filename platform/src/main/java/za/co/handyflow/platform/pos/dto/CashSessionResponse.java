package za.co.handyflow.platform.pos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashSessionResponse(
        UUID       id,
        String     sessionNumber,
        UUID       openedBy,
        String     openedByName,
        UUID       closedBy,
        String     closedByName,
        BigDecimal openingFloat,
        BigDecimal closingFloat,
        BigDecimal expectedCash,
        BigDecimal cashVariance,
        BigDecimal totalSales,
        int        transactionCount,
        String     status,
        String     notes,
        Instant    openedAt,
        Instant    closedAt
) {}
