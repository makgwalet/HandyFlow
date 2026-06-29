package za.co.handyflow.platform.security.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ClientPortalResponse — read-only site view for external clients.
 *
 * Intentionally minimal: enough for the client to verify guards are on-site
 * and see any open incidents.  Not a full data dump.
 *
 * shifts and incidents are Map<String, Object> because the portal data is
 * assembled from raw JDBC queries and serialised directly to JSON.  This avoids
 * creating a parallel set of DTOs just for the portal — the keys match exactly
 * what the frontend expects.
 */
public record ClientPortalResponse(
        UUID   siteId,
        String siteName,
        String contractStatus,
        LocalDate contractStart,
        LocalDate contractEnd,
        int    activeGuardsNow,
        int    weeklyCheckpointScans,
        List<Map<String, Object>> shifts,
        List<Map<String, Object>> openIncidents
) {}
