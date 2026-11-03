package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only schedule analysis for one project, measured from {@code dataDate} (today, SAST).
 *
 * Variance sign convention: POSITIVE = later than baseline (slipping), negative = ahead.
 * Variances are calendar days; floats are working days.
 *
 * @param plannedFinish   latest planned end across scheduled tasks
 * @param baselineFinish  the project's locked baseline end (null before approval)
 * @param forecastFinish  critical-path forecast completion
 * @param finishVarianceDays forecastFinish vs baselineFinish (null if either is missing)
 * @param cycleDetected   true if the dependency graph contains a cycle (forecast unavailable)
 * @param unscheduledTaskIds tasks excluded from the forecast for lacking planned start/end
 */
public record ScheduleReportResponse(
        UUID      projectId,
        LocalDate dataDate,
        LocalDate plannedStart,
        LocalDate plannedFinish,
        LocalDate baselineFinish,
        LocalDate forecastFinish,
        Integer   finishVarianceDays,
        boolean   cycleDetected,
        int       criticalTaskCount,
        List<TaskView>   tasks,
        List<Violation>  violations,
        List<UUID>       unscheduledTaskIds,
        List<PhaseRollup> phases
) {

    public record TaskView(
            UUID id, String taskNumber, String title, String status,
            LocalDate plannedStart, LocalDate plannedEnd,
            LocalDate baselineStart, LocalDate baselineEnd,
            LocalDate forecastStart, LocalDate forecastEnd,
            Integer floatDays, boolean critical, boolean overdue,
            Integer startVarianceDays, Integer finishVarianceDays,
            boolean outsideProjectDates
    ) {}

    public record Violation(
            UUID dependencyId, UUID predecessorId, UUID successorId,
            String predecessorNumber, String successorNumber,
            String type, int lagDays, int shortfallWorkingDays, String message
    ) {}

    public record PhaseRollup(
            UUID phaseId, String name, int taskCount, int completedCount,
            LocalDate plannedStart, LocalDate forecastFinish, BigDecimal avgProgressPct,
            LocalDate phaseEndDate, boolean forecastAfterPhaseEnd
    ) {}
}