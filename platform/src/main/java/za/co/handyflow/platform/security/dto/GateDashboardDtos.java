// security/dto/GateDashboardDtos.java
package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The gate dashboard across sites. People are shown by name only: ID numbers and phone numbers stay on the gate log. */
public final class GateDashboardDtos {
    private GateDashboardDtos() {}

    public record OnSiteRow(UUID id, UUID siteId, String siteName, String accessPointName, String entryType, String personName,
                            String company, String hostName, String vehicleRegistration, Instant loggedInAt, String status) {}
    public record SiteCount(UUID siteId, String siteName, int onSite, int enteredToday) {}
    public record Counts(int onSiteNow, int overstayed, int enteredToday, int departedToday, Map<String, Integer> onSiteByType, int vehiclesOnSite) {}
    /** One gate (access point): who is on site that came through it, and today's movement. */
    public record GateCount(UUID accessPointId, String accessPointName, UUID siteId, String siteName,
                            int onSite, int overstayed, int enteredToday, int departedToday) {}
    public record Dashboard(Counts counts, List<OnSiteRow> onSite, boolean onSiteTruncated, List<SiteCount> bySite, List<GateCount> byGate, Instant todayStartedAt) {}
}
