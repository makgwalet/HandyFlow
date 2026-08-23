package za.co.handyflow.platform.security.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FIX: backlog 7.1 — see SiteCoverageReport's own Javadoc for why
 * pulledShifts is a genuine separate bucket, not folded into
 * completedShifts/missedShifts.
 */
public record MonthlySummaryReport(
        UUID   tenantId,
        String month,
        int    totalShifts,
        int    completedShifts,
        int    missedShifts,
        int    pulledShifts,
        double totalGuardHours,
        double overallCompletionRatePct,
        int    totalIncidents,
        Map<String, Long> incidentsBySeverity,
        int    activeGuards,
        List<SiteSummary> siteSummaries
) {
    public record SiteSummary(
            UUID   siteId,
            String siteName,
            int    totalShifts,
            int    completedShifts,
            int    missedShifts,
            int    pulledShifts,
            double guardHours,
            double coverageRatePct,
            int    incidents
    ) {}
}