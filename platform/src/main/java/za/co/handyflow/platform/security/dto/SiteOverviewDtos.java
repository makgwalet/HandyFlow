// security/dto/SiteOverviewDtos.java
package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Everything the site detail page shows, from one call. Real records only; empty lists mean nothing on record. */
public final class SiteOverviewDtos {
    private SiteOverviewDtos() {}

    public record Counts(int guardsOnSite, int upcomingShifts7d, int openIncidents, int activePatrolRoutes, int checkpoints) {}
    public record IncidentRow(UUID id, String title, String severity, String status, Instant reportedAt) {}
    public record CheckpointRow(UUID id, String name, int scans30d, Instant lastScanAt) {}
    public record UpcomingShift(UUID shiftId, UUID guardId, String guardName, Instant startAt, Instant endAt) {}
    public record Overview(SiteResponse site, Counts counts, List<LiveGuardResponse> onSite, List<IncidentRow> recentIncidents,
                           List<CheckpointRow> checkpoints, List<UpcomingShift> upcoming) {}
}
