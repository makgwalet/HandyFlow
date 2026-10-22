package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.ErRules;
import za.co.handyflow.platform.hr.domain.model.ReportRules;
import za.co.handyflow.platform.hr.domain.model.ReportRules.Group;
import za.co.handyflow.platform.hr.dto.AttendanceDtos.RegisterRow;
import za.co.handyflow.platform.hr.dto.ReportDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * The HR reports. Each is read-only and built from what the other HR modules keep. The rules for who sees what live here: gender, race and
 * disability need HR_SENSITIVE_READ, payroll needs a compensation right, employee relations needs an employee relations right, and
 * confidential cases are left out unless the person may see sensitive records. Names are listed only for people who may see every employee.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    static final int NAMES_SHOWN = 25;

    private final JdbcTemplate jdbc;
    private final AttendanceRegister attendance;

    java.time.Clock clock = java.time.Clock.system(SAST);

    private LocalDate today() { return LocalDate.now(clock); }

    // ── Workforce ────────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Workforce workforce(TenantId tenantId, LocalDate from, LocalDate to, Set<String> held) {
        period(from, to);
        UUID t = tenantId.getValue();
        List<Map<String, Object>> closing = jdbc.queryForList(
                "SELECT department, employment_type, start_date, date_of_birth, gender, race, disability FROM hr_employees " +
                "WHERE tenant_id = ? AND deleted_at IS NULL AND start_date <= ? AND (end_date IS NULL OR end_date > ?)", t, to, to);
        int opening = count(jdbc.queryForList(
                "SELECT COUNT(*) AS n FROM hr_employees WHERE tenant_id = ? AND deleted_at IS NULL AND start_date < ? AND (end_date IS NULL OR end_date >= ?)", t, from, from));
        int joiners = count(jdbc.queryForList(
                "SELECT COUNT(*) AS n FROM hr_employees WHERE tenant_id = ? AND deleted_at IS NULL AND start_date BETWEEN ? AND ?", t, from, to));
        List<Map<String, Object>> left = jdbc.queryForList(
                "SELECT termination_reason FROM hr_employees WHERE tenant_id = ? AND deleted_at IS NULL AND end_date BETWEEN ? AND ?", t, from, to);

        Map<String, Integer> dept = new TreeMap<>(), type = new TreeMap<>(), tenure = new HashMap<>(), age = new HashMap<>(),
                gender = new TreeMap<>(), race = new TreeMap<>(), disability = new TreeMap<>(), reasons = new TreeMap<>();
        for (Map<String, Object> r : closing) {
            bump(dept, label(s(r, "department"), "No department"));
            bump(type, label(words(s(r, "employment_type")), "Not recorded"));
            bump(tenure, ReportRules.tenureBand(date(r, "start_date"), to));
            bump(age, ReportRules.ageBand(date(r, "date_of_birth"), to));
            bump(gender, label(words(s(r, "gender")), "Not recorded"));
            bump(race, label(words(s(r, "race")), "Not recorded"));
            bump(disability, Boolean.TRUE.equals(r.get("disability")) ? "Has a disability" : "No disability recorded");
        }
        for (Map<String, Object> r : left) bump(reasons, label(words(s(r, "termination_reason")), "Not recorded"));

        int closingCount = closing.size();
        BigDecimal rate = ReportRules.turnoverPercent(left.size(), opening, closingCount);
        boolean demographics = HrAccess.sensitive(held);
        List<String> notes = new ArrayList<>();
        notes.add("Headcount counts everyone with a start date on or before the day and no end date before it, including people on leave or suspended.");
        notes.add("Turnover is leavers divided by the average of the opening and closing headcount. The yearly figure scales the period's rate to 365 days.");
        if (demographics) notes.add("Groups of fewer than " + ReportRules.MIN_GROUP + " people are hidden, and when only one group would be hidden the next smallest is hidden too.");
        else notes.add("Gender, race and disability figures need access to sensitive records.");
        if (ReportRules.days(from, to) < 90) notes.add("Over a short period the yearly turnover figure is not a good guide.");
        return new Workforce(from, to, opening, closingCount, joiners, left.size(), rate, ReportRules.annualise(rate, ReportRules.days(from, to)),
                ReportRules.groups(dept, List.of()), ReportRules.groups(type, List.of()), ReportRules.groups(tenure, ReportRules.TENURE_BANDS),
                ReportRules.groups(age, ReportRules.AGE_BANDS), ReportRules.groups(reasons, List.of()), demographics,
                demographics ? ReportRules.suppressed(gender) : List.of(), demographics ? ReportRules.suppressed(race) : List.of(),
                demographics ? ReportRules.suppressed(disability) : List.of(), notes);
    }

    // ── Time and leave ───────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TimeReport time(TenantId tenantId, LocalDate from, LocalDate to, Set<String> held, UUID actor) {
        period(from, to);
        UUID t = tenantId.getValue();
        List<LeaveLine> leave = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT leave_type, COUNT(*) AS n, COALESCE(SUM(days_requested), 0) AS days FROM hr_leave_requests " +
                "WHERE tenant_id = ? AND status = 'APPROVED' AND start_date BETWEEN ? AND ? GROUP BY leave_type ORDER BY leave_type", t, from, to)) {
            leave.add(new LeaveLine(s(r, "leave_type"), num(r, "n").longValue(), dec(r, "days")));
        }
        Map<String, Object> pending = first(jdbc.queryForList(
                "SELECT COUNT(*) AS n, COUNT(*) FILTER (WHERE created_at < ?) AS stale FROM hr_leave_requests WHERE tenant_id = ? AND status = 'PENDING'",
                Timestamp.from(Instant.now(clock).minusSeconds(ReportRules.STALE_LEAVE_DAYS * 86400L)), t));
        Map<String, Object> sick = first(jdbc.queryForList(
                "SELECT COALESCE(SUM(days_requested), 0) AS days, COUNT(DISTINCT employee_id) AS people FROM hr_leave_requests " +
                "WHERE tenant_id = ? AND status = 'APPROVED' AND leave_type = 'SICK' AND start_date BETWEEN ? AND ?", t, from, to));

        AttendanceSummary summary = null;
        String attendanceNote = null;
        try {
            List<RegisterRow> rows = attendance.register(tenantId, from, to, held, actor);
            summary = summarise(rows);
        } catch (AccessDeniedException e) {
            attendanceNote = "Attendance is not shown because you do not have access to the attendance register.";
        } catch (IllegalArgumentException e) {
            attendanceNote = "Attendance is not shown for this period: " + e.getMessage();
        }

        boolean money = HrAccess.compensation(held);
        List<Line> byKind = new ArrayList<>(), byDept = new ArrayList<>();
        String hoursSql = "COALESCE(SUM(CASE WHEN c.hourly_rate > 0 AND c.multiplier > 0 THEN c.amount / (c.hourly_rate * c.multiplier) ELSE 0 END), 0)";
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT c.kind AS label, COUNT(*) AS n, COALESCE(SUM(c.amount), 0) AS amount, " + hoursSql + " AS hours FROM hr_overtime_claims c " +
                "WHERE c.tenant_id = ? AND c.status = 'APPROVED' AND c.work_date BETWEEN ? AND ? GROUP BY c.kind ORDER BY c.kind", t, from, to)) {
            byKind.add(line(r, money));
        }
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT COALESCE(NULLIF(e.department, ''), 'No department') AS label, COUNT(*) AS n, COALESCE(SUM(c.amount), 0) AS amount, " + hoursSql + " AS hours " +
                "FROM hr_overtime_claims c JOIN hr_employees e ON e.id = c.employee_id " +
                "WHERE c.tenant_id = ? AND c.status = 'APPROVED' AND c.work_date BETWEEN ? AND ? GROUP BY 1 ORDER BY 1", t, from, to)) {
            byDept.add(line(r, money));
        }
        long otPending = num(first(jdbc.queryForList("SELECT COUNT(*) AS n FROM hr_overtime_claims WHERE tenant_id = ? AND status = 'PENDING'", t)), "n").longValue();

        List<String> notes = new ArrayList<>();
        notes.add("Leave is counted in the period its first day falls in, using the days requested. A leave that crosses the end of the period is not split.");
        notes.add("Overtime is approved claims by the date worked. Hours are worked out from the amount, the hourly rate and the multiplier.");
        if (!money) notes.add("Overtime amounts need a compensation right.");
        return new TimeReport(from, to, leave, num(pending, "n").longValue(), num(pending, "stale").longValue(), dec(sick, "days"), num(sick, "people").longValue(),
                summary, attendanceNote, byKind, byDept, otPending, notes);
    }

    static AttendanceSummary summarise(List<RegisterRow> rows) {
        int present = 0, late = 0, absent = 0, onLeave = 0;
        long worked = 0, overtime = 0;
        for (RegisterRow r : rows) {
            present += r.presentDays(); late += r.lateDays(); absent += r.absentDays(); onLeave += r.leaveDays();
            worked += r.workedMinutes(); overtime += r.overtimeMinutes();
        }
        List<AttendanceOutlier> worst = rows.stream().filter(r -> r.absentDays() > 0)
                .sorted(Comparator.comparingInt(RegisterRow::absentDays).reversed().thenComparing(RegisterRow::employeeName, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(5).map(r -> new AttendanceOutlier(r.employeeNumber(), r.employeeName(), r.department(), r.absentDays(), r.lateDays())).toList();
        return new AttendanceSummary(rows.size(), present, late, absent, onLeave, ReportRules.hours(worked), ReportRules.hours(overtime), worst);
    }

    // ── Payroll ──────────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PayrollReport payroll(TenantId tenantId, LocalDate from, LocalDate to, Set<String> held) {
        period(from, to);
        if (!HrAccess.compensation(held)) throw new AccessDeniedException("You do not have access to payroll figures.");
        UUID t = tenantId.getValue();
        List<RunLine> runs = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO, overtime = BigDecimal.ZERO, paye = BigDecimal.ZERO, uifEe = BigDecimal.ZERO, uifEr = BigDecimal.ZERO,
                sdl = BigDecimal.ZERO, net = BigDecimal.ZERO, cost = BigDecimal.ZERO;
        int counted = 0;
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT r.pay_run_number, r.period_start, r.period_end, r.pay_date, r.status, COUNT(p.id) AS n, " +
                "COALESCE(SUM(p.total_earnings), 0) AS gross, COALESCE(SUM(p.overtime_amount), 0) AS overtime, COALESCE(SUM(p.paye_amount), 0) AS paye, " +
                "COALESCE(SUM(p.uif_employee), 0) AS uif_ee, COALESCE(SUM(p.uif_employer), 0) AS uif_er, COALESCE(SUM(p.sdl_amount), 0) AS sdl, " +
                "COALESCE(SUM(p.net_pay), 0) AS net FROM hr_pay_runs r LEFT JOIN hr_payslips p ON p.pay_run_id = r.id " +
                "WHERE r.tenant_id = ? AND r.status <> 'CANCELLED' AND r.pay_date BETWEEN ? AND ? GROUP BY r.id ORDER BY r.pay_date, r.pay_run_number", t, from, to)) {
            String status = s(r, "status");
            boolean counts = ReportRules.countsTowardsTotals(status);
            BigDecimal g = dec(r, "gross"), ot = dec(r, "overtime"), pa = dec(r, "paye"), ue = dec(r, "uif_ee"), ur = dec(r, "uif_er"), sd = dec(r, "sdl"), ne = dec(r, "net");
            BigDecimal c = ReportRules.employerCost(g, ur, sd);
            runs.add(new RunLine(s(r, "pay_run_number"), date(r, "period_start"), date(r, "period_end"), date(r, "pay_date"), status, num(r, "n").intValue(),
                    g, pa, ue, ur, sd, ne, c, counts));
            if (counts) {
                counted++; gross = gross.add(g); overtime = overtime.add(ot); paye = paye.add(pa); uifEe = uifEe.add(ue); uifEr = uifEr.add(ur);
                sdl = sdl.add(sd); net = net.add(ne); cost = cost.add(c);
            }
        }
        List<Line> byDept = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT COALESCE(NULLIF(e.department, ''), 'No department') AS label, COUNT(DISTINCT p.employee_id) AS n, " +
                "COALESCE(SUM(p.total_earnings + p.uif_employer + p.sdl_amount), 0) AS amount FROM hr_payslips p " +
                "JOIN hr_pay_runs r ON r.id = p.pay_run_id JOIN hr_employees e ON e.id = p.employee_id " +
                "WHERE r.tenant_id = ? AND r.status IN ('FINALISED', 'COMPLETED') AND r.pay_date BETWEEN ? AND ? GROUP BY 1 ORDER BY 3 DESC", t, from, to)) {
            byDept.add(new Line(s(r, "label"), num(r, "n").longValue(), dec(r, "amount"), null));
        }
        LocalDate today = today();
        List<Emp201Line> emp201 = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT period_start, period_end, due_date, total_paye, total_uif, total_sdl, total_payable, status, payment_ref FROM hr_emp201 " +
                "WHERE tenant_id = ? AND period_end BETWEEN ? AND ? ORDER BY period_end", t, from, to)) {
            String st = s(r, "status");
            LocalDate due = date(r, "due_date");
            emp201.add(new Emp201Line(date(r, "period_start"), date(r, "period_end"), due, dec(r, "total_paye"), dec(r, "total_uif"), dec(r, "total_sdl"),
                    dec(r, "total_payable"), st, s(r, "payment_ref"), !"PAID".equals(st) && due != null && due.isBefore(today)));
        }
        List<String> notes = List.of(
                "Pay runs are chosen by their pay date. Only finalised and completed runs count towards the totals; other runs are listed so they are not missed.",
                "Gross is total earnings. Employer cost is total earnings plus the employer's UIF and the skills levy. It does not include employer medical aid or retirement contributions, which are not yet recorded.",
                "Departments are each employee's current department, not the one they had when they were paid.",
                "EMP201 lines are chosen by the end of their period. A line is overdue when it is not marked paid and its due date has passed.");
        return new PayrollReport(from, to, runs, new PayrollTotals(gross, overtime, paye, uifEe, uifEr, sdl, net, cost, counted), byDept, emp201, notes);
    }

    // ── Employee relations and compliance ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Compliance compliance(TenantId tenantId, LocalDate from, LocalDate to, Set<String> held) {
        period(from, to);
        UUID t = tenantId.getValue();
        boolean names = HrAccess.seesAll(held);
        List<Check> checks = new ArrayList<>();
        String live = "FROM hr_employees WHERE tenant_id = ? AND deleted_at IS NULL AND status <> 'TERMINATED' AND ";
        checks.add(check("NO_ID", "No ID number", live + "(id_number IS NULL OR TRIM(id_number) = '')", names, t,
                "The ID number is needed for the tax certificate and to check the right to work."));
        checks.add(check("NO_TAX", "No tax number", live + "(tax_number IS NULL OR TRIM(tax_number) = '')", names, t,
                "Without a tax number the employee's PAYE cannot be reported to SARS correctly."));
        checks.add(check("NO_BANK", "No bank account", live + "(bank_account_number IS NULL OR TRIM(bank_account_number) = '')", names, t,
                "The employee cannot be paid by transfer."));
        checks.add(check("NO_EMERGENCY", "No emergency contact", live + "(emergency_contact_name IS NULL OR TRIM(emergency_contact_name) = '' OR emergency_contact_phone IS NULL OR TRIM(emergency_contact_phone) = '')", names, t,
                "Needed if the employee is hurt or ill at work."));
        checks.add(check("NO_MANAGER", "No manager recorded", live + "manager_id IS NULL", names, t,
                "Without a manager there is nobody to approve leave or write a performance review."));
        Instant staleBefore = Instant.now(clock).minusSeconds(ReportRules.STALE_LEAVE_DAYS * 86400L);
        long staleLeave = num(first(jdbc.queryForList("SELECT COUNT(*) AS n FROM hr_leave_requests WHERE tenant_id = ? AND status = 'PENDING' AND created_at < ?",
                t, Timestamp.from(staleBefore))), "n").longValue();
        checks.add(new Check("STALE_LEAVE", "Leave requests waiting more than " + ReportRules.STALE_LEAVE_DAYS + " days", staleLeave, List.of(),
                "Employees are left not knowing whether they can take the leave."));
        long overduePlans = num(first(jdbc.queryForList("SELECT COUNT(*) AS n FROM hr_perf_plans WHERE tenant_id = ? AND status = 'ACTIVE' AND end_date < ?", t, today())), "n").longValue();
        checks.add(new Check("PLANS_OVERDUE", "Improvement plans past their end date with no outcome", overduePlans, List.of(),
                "A plan should be closed as met or not met when its time is up."));

        ErSummary er = ErRules.canRead(held) ? employeeRelations(t, from, to, held) : null;
        List<String> notes = new ArrayList<>();
        notes.add("Data checks look at everyone who is not terminated. " + (names ? "Up to " + NAMES_SHOWN + " names are listed for each." : "Names are not listed because you can see only part of the workforce."));
        if (er == null) notes.add("Employee relations figures need an employee relations right.");
        else if (!HrAccess.sensitive(held)) notes.add("Confidential cases are left out of the employee relations figures.");
        return new Compliance(from, to, checks, er, notes);
    }

    private Check check(String code, String title, String fromWhere, boolean names, UUID tenant, String why) {
        long n = num(first(jdbc.queryForList("SELECT COUNT(*) AS n " + fromWhere, tenant)), "n").longValue();
        List<String> people = new ArrayList<>();
        if (names && n > 0) {
            for (Map<String, Object> r : jdbc.queryForList("SELECT employee_number, first_name, last_name " + fromWhere + " ORDER BY employee_number LIMIT " + NAMES_SHOWN, tenant)) {
                people.add((s(r, "employee_number") == null ? "" : s(r, "employee_number") + " ") + (s(r, "first_name") == null ? "" : s(r, "first_name")) + " " + (s(r, "last_name") == null ? "" : s(r, "last_name")));
            }
        }
        return new Check(code, title, n, people, why);
    }

    private ErSummary employeeRelations(UUID t, LocalDate from, LocalDate to, Set<String> held) {
        boolean sensitive = HrAccess.sensitive(held);
        String visible = sensitive ? "" : " AND confidential = false";
        LocalDate today = today();
        long opened = num(first(jdbc.queryForList("SELECT COUNT(*) AS n FROM hr_er_cases WHERE tenant_id = ? AND reported_on BETWEEN ? AND ?" + visible, t, from, to)), "n").longValue();
        Map<String, Object> closedRow = first(jdbc.queryForList(
                "SELECT COUNT(*) AS n, AVG(closed_on - reported_on) AS avg_days FROM hr_er_cases WHERE tenant_id = ? AND closed_on BETWEEN ? AND ?" + visible, t, from, to));
        long open = num(first(jdbc.queryForList("SELECT COUNT(*) AS n FROM hr_er_cases WHERE tenant_id = ? AND status NOT IN ('CLOSED', 'WITHDRAWN')" + visible, t)), "n").longValue();
        long longOpen = num(first(jdbc.queryForList("SELECT COUNT(*) AS n FROM hr_er_cases WHERE tenant_id = ? AND status NOT IN ('CLOSED', 'WITHDRAWN') AND reported_on < ?" + visible,
                t, today.minusDays(ReportRules.LONG_CASE_DAYS))), "n").longValue();
        long overdue = num(first(jdbc.queryForList("SELECT COUNT(*) AS n FROM hr_er_cases WHERE tenant_id = ? AND status = 'NOTICE_ISSUED' AND hearing_date < ? AND hearing_held_on IS NULL" + visible,
                t, today)), "n").longValue();
        long expiring = num(first(jdbc.queryForList("SELECT COUNT(*) AS n FROM hr_er_cases WHERE tenant_id = ? AND warning_valid_until BETWEEN ? AND ?" + visible,
                t, today, today.plusDays(ReportRules.WARNING_SOON_DAYS))), "n").longValue();
        Map<String, Integer> types = new TreeMap<>(), categories = new TreeMap<>(), outcomes = new TreeMap<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT case_type, category FROM hr_er_cases WHERE tenant_id = ? AND reported_on BETWEEN ? AND ?" + visible, t, from, to)) {
            bump(types, label(words(s(r, "case_type")), "Not recorded"));
            bump(categories, label(words(s(r, "category")), "Not recorded"));
        }
        for (Map<String, Object> r : jdbc.queryForList("SELECT outcome FROM hr_er_cases WHERE tenant_id = ? AND decided_on BETWEEN ? AND ? AND outcome IS NOT NULL" + visible, t, from, to)) {
            bump(outcomes, label(words(s(r, "outcome")), "Not recorded"));
        }
        long left = sensitive ? 0 : num(first(jdbc.queryForList(
                "SELECT COUNT(*) AS n FROM hr_er_cases WHERE tenant_id = ? AND confidential = true AND (reported_on BETWEEN ? AND ? OR status NOT IN ('CLOSED', 'WITHDRAWN'))", t, from, to)), "n").longValue();
        BigDecimal avg = closedRow.get("avg_days") == null ? null : dec(closedRow, "avg_days").setScale(1, java.math.RoundingMode.HALF_UP);
        return new ErSummary(opened, num(closedRow, "n").longValue(), open, longOpen, overdue, expiring, avg,
                ReportRules.groups(types, List.of()), ReportRules.groups(categories, List.of()), ReportRules.groups(outcomes, List.of()), left);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

    private static void period(LocalDate from, LocalDate to) {
        String problem = ReportRules.periodProblem(from, to);
        if (problem != null) throw new IllegalArgumentException(problem);
    }

    private static void bump(Map<String, Integer> m, String key) { m.merge(key, 1, Integer::sum); }

    private static String label(String v, String fallback) { return v == null || v.isBlank() ? fallback : v; }

    /** SOME_CODE becomes "Some code". Free text is left as it is. */
    static String words(String v) {
        if (v == null || v.isBlank()) return null;
        String t = v.trim();
        if (!t.equals(t.toUpperCase())) return t;
        String lower = t.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static Line line(Map<String, Object> r, boolean money) {
        return new Line(s(r, "label"), num(r, "n").longValue(), money ? dec(r, "amount") : null, dec(r, "hours").setScale(1, java.math.RoundingMode.HALF_UP));
    }

    private static Map<String, Object> first(List<Map<String, Object>> rows) { return rows.isEmpty() ? Map.of() : rows.get(0); }

    private static int count(List<Map<String, Object>> rows) { return num(first(rows), "n").intValue(); }

    private static Number num(Map<String, Object> r, String k) { return r.get(k) instanceof Number n ? n : Long.valueOf(0); }

    private static BigDecimal dec(Map<String, Object> r, String k) {
        Object v = r.get(k);
        if (v instanceof BigDecimal b) return b;
        return v instanceof Number n ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
    }

    private static String s(Map<String, Object> r, String k) { Object v = r.get(k); return v == null ? null : v.toString(); }

    private static LocalDate date(Map<String, Object> r, String k) {
        Object v = r.get(k);
        if (v instanceof LocalDate d) return d;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        return null;
    }
}
