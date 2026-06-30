package za.co.handyflow.platform.security.dto;

import java.util.UUID;

public record PatrolRouteResponse(
        UUID    id,
        UUID siteId,
        String  siteName,
        String  name,
        int     intervalMinutes,
        int     toleranceMinutes,
        int     checkpointCount,
        boolean active
) {}