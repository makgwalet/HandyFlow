package za.co.handyflow.platform.security.dto;

import java.util.List;
import java.util.UUID;

/**
 * FIX: backlog 7.1 — see SiteCoverageReport's own Javadoc for why
 * pulledShifts is a genuine separate bucket, not folded into
 * completedShifts/missedShifts.
 */
public record GuardAttendanceReport(
        UUID   guardId,
        String guardName,
        String month,
        int    totalShifts,
        int    completedShifts,
        int    missedShifts,
        int    cancelledShifts,
        int    pulledShifts,
        double totalHoursWorked,
        double attendanceRatePct,
        int    checkpointScans,
        int    incidentsLogged,
        List<SiteAttendance> siteBreakdown
) {
    public record SiteAttendance(
            UUID   siteId,
            String siteName,
            int    totalShifts,
            int    completedShifts,
            int    missedShifts,
            int    pulledShifts,
            double hoursWorked
    ) {}
}