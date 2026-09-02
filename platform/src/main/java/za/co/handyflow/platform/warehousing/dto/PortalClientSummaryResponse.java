package za.co.handyflow.platform.warehousing.dto;

import java.util.UUID;

/** Direct mirror of every sibling provider module's own PortalClientSummaryResponse. */
public record PortalClientSummaryResponse(UUID clientId, String tradingName) {}
