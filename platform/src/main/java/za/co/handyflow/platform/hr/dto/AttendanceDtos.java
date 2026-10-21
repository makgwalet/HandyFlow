package za.co.handyflow.platform.hr.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for attendance. Times are instants; days are South African dates. */
public final class AttendanceDtos {

    private AttendanceDtos() {}

    public record ScheduleRequest(String name, List<String> days, LocalTime startTime, LocalTime endTime, Integer breakMinutes) {}

    public record Schedule(UUID id, String name, List<String> days, LocalTime startTime, LocalTime endTime, int breakMinutes,
                           boolean isDefault, boolean active, int dailyMinutes, int weeklyMinutes, List<String> warnings) {}

    public record AssignRequest(UUID scheduleId) {}

    public record LogLine(String action, Instant oldIn, Instant oldOut, Instant newIn, Instant newOut, String reason, UUID doneBy, Instant doneAt) {}

    public record Entry(UUID id, Instant clockIn, Instant clockOut, String source, boolean corrected, List<LogLine> log) {}

    public record Day(LocalDate date, String status, int expectedMinutes, int workedMinutes, int lateMinutes, int shortMinutes,
                      String overtimeKind, int overtimeMinutes, List<String> flags, List<Entry> entries,
                      UUID overtimeClaimId, String overtimeClaimStatus) {}

    public record Timesheet(UUID employeeId, String employeeNumber, String employeeName, String scheduleName, int weeklyMinutes,
                            LocalDate from, LocalDate to, List<Day> days, List<String> weekFlags,
                            int workedMinutes, int lateDays, int absentDays, int overtimeMinutes, boolean canCorrect, boolean aboveThreshold) {}

    public record Mine(UUID employeeId, boolean clockedIn, Instant since, String scheduleName, Timesheet recent) {}

    public record RegisterRow(UUID employeeId, String employeeNumber, String employeeName, String department,
                              int presentDays, int lateDays, int absentDays, int leaveDays, int workedMinutes, int overtimeMinutes, int flagged) {}

    public record EntryRequest(UUID employeeId, Instant clockIn, Instant clockOut, String reason) {}

    public record VoidRequest(String reason) {}

    public record ActiveRequest(boolean active) {}
}
