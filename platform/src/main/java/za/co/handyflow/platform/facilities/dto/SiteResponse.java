package za.co.handyflow.platform.facilities.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SiteResponse(
        UUID id, String name, String siteType, Map<String, String> address,
        String notes, String status, Instant createdAt
) {}
