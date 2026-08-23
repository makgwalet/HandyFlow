package za.co.handyflow.platform.security.dto;

import java.util.Map;
import java.util.UUID;

/**
 * FIX: backlog 7.1 — added pulledShifts. PULLED is deliberately its own
 * bucket, not folded into completedShifts or missedShifts — a supervisor
 * pull is neither a normal full completion nor a no-show; the guard did
 * show up and work part of the shift (per the confirmed decision, its
 * partial hours now count in totalGuardHours), but collapsing it into
 * either existing bucket would misrepresent what actually happened.
 */
public record SiteCoverageReport(
        UUID   siteId,
        String siteName,
        String month,
        int    totalShifts,
        int    completedShifts,
        int    missedShifts,
        int    cancelledShifts,
        int    pulledShifts,
        double totalGuardHours,
        double shiftCompletionRatePct,
        int    patrolRoundsExpected,
        int    patrolRoundsCompleted,
        int    patrolRoundsMissed,
        int    checkpointScans,
        int    totalIncidents,
        Map<String, Long> incidentsBySeverity
) {}