// security/dto/SecurityDashboardDtos.java
package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The Security module's landing dashboard: figures for now and today, a ranked "needs attention" list and short row lists. */
public final class SecurityDashboardDtos {

    private SecurityDashboardDtos() {}

    public record Shifts(int onDuty, int scheduledToday, int notStarted, int missedToday, int completedToday) {}
    public record Workforce(int totalGuards, int activeGuards, int psiraExpired, int psiraExpiring,
                            int competenciesExpired, int competenciesExpiring) {}
    public record Incidents(int open, int unacknowledged, int criticalOpen, int last7Days) {}
    public record Complaints(int open, int urgent) {}
    public record Gate(int onSite, int overstayed, int enteredToday) {}

    /** `level` is DANGER, WARNING or INFO. `section` is the Security section that deals with it. */
    public record AttentionItem(String code, String level, String title, String detail, int count, String section) {}

    public record ActiveShiftRow(UUID id, UUID guardId, String guardName, UUID siteId, String siteName, Instant startAt, Instant endAt,
                                 Instant actualStartAt, Integer minutesLate) {}
    public record OpenIncidentRow(UUID id, String title, String severity, String status, UUID siteId, String siteName, Instant createdAt) {}

    public record Dashboard(Instant asOf, int activeSites, int openAlarms, Shifts shifts, Workforce workforce, Incidents incidents,
                            Complaints complaints, Gate gate, List<AttentionItem> attention,
                            List<ActiveShiftRow> activeShifts, List<OpenIncidentRow> openIncidents) {}
}
