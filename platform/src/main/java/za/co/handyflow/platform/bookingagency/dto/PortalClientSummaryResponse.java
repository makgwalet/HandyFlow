package za.co.handyflow.platform.bookingagency.dto;

import java.util.UUID;

public record PortalClientSummaryResponse(UUID clientId, String tradingName) {}