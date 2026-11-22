package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FleetHealthResponse(Instant generatedAt, int activeDevices, int withIssues, int critical, List<Item> items) {
    /** One phone with at least one problem. {@code issues} are OFFLINE, NEVER_SEEN, CRITICAL_BATTERY, LOW_BATTERY, OLD_APP. */
    public record Item(UUID deviceId, String deviceName, String deviceType, UUID siteId, UUID guardId, String guardName,
                       Instant lastSeenAt, Integer batteryPct, String appVersion, boolean onSession,
                       boolean critical, List<String> issues) {}
}
