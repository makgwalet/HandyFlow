package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FmSiteResponse(
        UUID id, UUID clientId, String name, String siteType, Map<String, String> address,
        String notes, String status, Instant createdAt
) {}
