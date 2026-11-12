package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What a site supervisor sees in the Guard app about the site they are on shift at. */
public final class GuardSupervisorDtos {
    private GuardSupervisorDtos() {}

    public record SetSiteSupervisorRequest(boolean enabled) {}

    public record OnDutyGuard(
            UUID guardId, String name, Instant shiftStart, Instant shiftEnd, boolean overrunning,
            String gpsState, Instant lastScanAt, String lastScanCheckpoint,
            int roundsTotal, int roundsComplete, int roundsMissed) {}

    public record OpenIncident(UUID id, String title, String severity, String status, Instant createdAt) {}

    public record DecideSwapRequest(@jakarta.validation.constraints.Size(max = 500) String reason) {}

    public record ResolveIncidentRequest(@jakarta.validation.constraints.Size(max = 1000) String note) {}

    public record SiteOverview(UUID siteId, String siteName, Instant generatedAt,
                               List<OnDutyGuard> onDuty, List<OpenIncident> openIncidents) {}
}
