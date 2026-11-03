package za.co.handyflow.platform.projects.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Outcome of a task date change: what moved, and what is STILL wrong afterwards —
 * so a date edit can never silently leave dependent tasks invalid.
 *
 * @param edited              the task the user changed
 * @param shifted             dependents moved because shiftDependents was requested
 * @param blocked             dependents that needed to move but are completed/started (left alone)
 * @param remainingViolations dependencies still broken by the planned dates
 */
public record TaskDatesChangeResponse(
        Shift edited,
        List<Shift> shifted,
        List<String> blocked,
        List<ScheduleReportResponse.Violation> remainingViolations
) {
    public record Shift(UUID taskId, String taskNumber,
                        LocalDate oldStart, LocalDate oldEnd,
                        LocalDate newStart, LocalDate newEnd,
                        int shiftWorkingDays) {}
}