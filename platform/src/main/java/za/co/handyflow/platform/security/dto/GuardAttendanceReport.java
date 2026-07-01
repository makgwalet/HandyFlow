package za.co.handyflow.platform.security.dto;

import java.util.List;
import java.util.UUID;

public record GuardAttendanceReport(
        UUID   guardId,
        String guardName,
        String month,
        int    totalShifts,
        int    completedShifts,
        int    missedShifts,
        int    cancelledShifts,
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
            double hoursWorked
    ) {}
}
