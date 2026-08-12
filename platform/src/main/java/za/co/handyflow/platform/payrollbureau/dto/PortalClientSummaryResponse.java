package za.co.handyflow.platform.payrollbureau.dto;

import java.util.UUID;

public record PortalClientSummaryResponse(UUID clientId, String tradingName) {}