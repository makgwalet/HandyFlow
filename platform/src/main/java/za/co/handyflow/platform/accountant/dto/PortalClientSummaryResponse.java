package za.co.handyflow.platform.accountant.dto;

import java.util.UUID;

public record PortalClientSummaryResponse(
        UUID clientId,
        String tradingName
) {
}