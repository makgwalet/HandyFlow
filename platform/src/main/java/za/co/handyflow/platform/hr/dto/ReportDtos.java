package za.co.handyflow.platform.hr.dto;

import za.co.handyflow.platform.hr.domain.model.ReportRules.Group;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the HR reports return. Every figure is worked out on the server; the screens only lay them out. */
public final class ReportDtos {

    private ReportDtos() {}

    /** A line of a table: a label with counts. */
    public record Line(String label, long count, BigDecimal amount, BigDecimal hours) {}

    // ── Workforce ────────────────────────────────────────────────────────────────────────────────
    public record Workforce(LocalDate from, LocalDate to, int opening, int closing, int joiners, int leavers,
                            BigDecimal turnoverPercent, BigDecimal annualisedTurnoverPercent,
                            List<Group> byDepartment, List<Group> byEmploymentType, List<Group> tenure, List<Group> age,
                            List<Group> leaversByReason, boolean demographicsShown, List<Group> gender, List<Group> race, List<Group> disability,
                            List<String> notes) {}

    // ── Time and leave ───────────────────────────────────────────────────────────────────────────
    public record LeaveLine(String leaveType, long requests, BigDecimal days) {}
    public record AttendanceSummary(int employees, int presentDays, int lateDays, int absentDays, int leaveDays, BigDecimal workedHours,
                                    BigDecimal overtimeHours, List<AttendanceOutlier> mostAbsent) {}
    public record AttendanceOutlier(String employeeNumber, String employeeName, String department, int absentDays, int lateDays) {}
    public record TimeReport(LocalDate from, LocalDate to, List<LeaveLine> leave, long leavePending, long leavePendingStale,
                             BigDecimal sickDays, long sickEmployees, AttendanceSummary attendance, String attendanceNote,
                             List<Line> overtimeByKind, List<Line> overtimeByDepartment, long overtimePending, List<String> notes) {}

    // ── Payroll ──────────────────────────────────────────────────────────────────────────────────
    public record RunLine(String number, LocalDate periodStart, LocalDate periodEnd, LocalDate payDate, String status, int employees,
                          BigDecimal gross, BigDecimal paye, BigDecimal uifEmployee, BigDecimal uifEmployer, BigDecimal sdl, BigDecimal net,
                          BigDecimal employerCost, boolean counted) {}
    public record PayrollTotals(BigDecimal gross, BigDecimal overtime, BigDecimal paye, BigDecimal uifEmployee, BigDecimal uifEmployer,
                                BigDecimal sdl, BigDecimal net, BigDecimal employerCost, int runs) {}
    public record Emp201Line(LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate, BigDecimal paye, BigDecimal uif, BigDecimal sdl,
                             BigDecimal payable, String status, String paymentRef, boolean overdue) {}
    public record PayrollReport(LocalDate from, LocalDate to, List<RunLine> runs, PayrollTotals totals, List<Line> byDepartment,
                                List<Emp201Line> emp201, List<String> notes) {}

    // ── Employee relations and compliance ────────────────────────────────────────────────────────
    public record Check(String code, String title, long count, List<String> people, String why) {}
    public record ErSummary(long opened, long closed, long open, long longOpen, long hearingsOverdue, long warningsExpiringSoon,
                            BigDecimal averageDaysToClose, List<Group> byType, List<Group> byCategory, List<Group> outcomes, long confidentialLeftOut) {}
    public record Compliance(LocalDate from, LocalDate to, List<Check> checks, ErSummary employeeRelations, List<String> notes) {}
}
