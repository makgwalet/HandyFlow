package za.co.handyflow.platform.collectionsagency.dto;

import java.util.UUID;

/** Direct mirror of every sibling provider module's own PortalClientSummaryResponse (bookingagency/recruitmentagency/payrollbureau). */
public record PortalClientSummaryResponse(UUID clientId, String tradingName) {}
