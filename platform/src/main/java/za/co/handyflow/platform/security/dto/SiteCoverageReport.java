package za.co.handyflow.platform.security.dto;

import java.util.Map;
import java.util.UUID;

public record SiteCoverageReport(
        UUID   siteId,
        String siteName,
        String month,
        int    totalShifts,
        int    completedShifts,
        int    missedShifts,
        int    cancelledShifts,
        double totalGuardHours,
        double shiftCompletionRatePct,
        int    patrolRoundsExpected,
        int    patrolRoundsCompleted,
        int    patrolRoundsMissed,
        int    checkpointScans,
        int    totalIncidents,
        Map<String, Long> incidentsBySeverity
) {}
