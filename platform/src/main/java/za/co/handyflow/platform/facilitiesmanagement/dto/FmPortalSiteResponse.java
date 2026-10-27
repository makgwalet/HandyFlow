package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** What a client contact may see of a site. EXCLUDES the staff-only {@code notes} field. */
public record FmPortalSiteResponse(
        UUID id, String name, String siteType, Map<String, String> address, String status, Instant createdAt
) {}