package za.co.handyflow.platform.security.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MonthlySummaryReport(
        UUID   tenantId,
        String month,
        int    totalShifts,
        int    completedShifts,
        int    missedShifts,
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
            double guardHours,
            double coverageRatePct,
            int    incidents
    ) {}
}
