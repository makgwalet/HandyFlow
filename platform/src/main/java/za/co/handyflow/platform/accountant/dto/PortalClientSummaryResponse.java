package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PortalClientSummaryResponse(
        UUID clientId,
        String tradingName,
        BigDecimal outstandingBalance,
        long openRequestCount,
        long upcomingDeadlineCount
) {}