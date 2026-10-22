package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.dto.AttendanceDtos.RegisterRow;
import za.co.handyflow.platform.hr.dto.ReportDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ReportServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final LocalDate FROM = LocalDate.of(2026, 7, 1), TO = LocalDate.of(2026, 9, 30);

    /** Answers a query with the rows registered for the first fragment found in its text, and remembers every query. */
    static class FakeJdbc extends JdbcTemplate {
        final Map<String, List<Map<String, Object>>> answers = new LinkedHashMap<>();
        final List<String> sql = new ArrayList<>();
        FakeJdbc on(String fragment, List<Map<String, Object>> rows) { answers.put(fragment, rows); return this; }
        @Override public List<Map<String, Object>> queryForList(String q, Object... args) {
            sql.add(q);
            for (Map.Entry<String, List<Map<String, Object>>> e : answers.entrySet()) if (q.contains(e.getKey())) return e.getValue();
            return List.of();
        }
        boolean ran(String fragment) { return sql.stream().anyMatch(q -> q.contains(fragment)); }
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
    private static List<Map<String, Object>> rows(Map<String, Object>... r) { return List.of(r); }
    private static Set<String> held(String... a) { return Set.of(a); }

    private ReportService service(FakeJdbc jdbc, AttendanceRegister attendance) {
        ReportService s = new ReportService(jdbc, attendance);
        s.clock = Clock.fixed(Instant.parse("2026-10-22T06:00:00Z"), ZoneId.of("Africa/Johannesburg"));
        return s;
    }
    private ReportService service(FakeJdbc jdbc) { return service(jdbc, (t, f, to, h, a) -> List.of()); }

    @SafeVarargs
    private static List<Map<String, Object>> people(int n, Map<String, Object>... template) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new HashMap<>(template[0]));
        return out;
    }

    // ── Workforce ────────────────────────────────────────────────────────────────────────────────

    private FakeJdbc workforceData() {
        List<Map<String, Object>> staff = new ArrayList<>();
        staff.addAll(people(12, row("department", "Finance", "employment_type", "PERMANENT", "start_date", LocalDate.of(2020, 3, 1), "date_of_birth", LocalDate.of(1985, 5, 5),
                "gender", "FEMALE", "race", "AFRICAN", "disability", false)));
        staff.addAll(people(6, row("department", "Operations", "employment_type", "FIXED_TERM", "start_date", LocalDate.of(2026, 8, 1), "date_of_birth", LocalDate.of(1999, 1, 1),
                "gender", "MALE", "race", "AFRICAN", "disability", false)));
        staff.addAll(people(2, row("department", null, "employment_type", "PERMANENT", "start_date", LocalDate.of(2010, 1, 1), "date_of_birth", null,
                "gender", "MALE", "race", "WHITE", "disability", true)));
        return new FakeJdbc()
                .on("SELECT department, employment_type", staff)
                .on("COUNT(*) AS n FROM hr_employees WHERE tenant_id = ? AND deleted_at IS NULL AND start_date < ?", rows(row("n", 17L)))
                .on("COUNT(*) AS n FROM hr_employees WHERE tenant_id = ? AND deleted_at IS NULL AND start_date BETWEEN", rows(row("n", 6L)))
                .on("SELECT termination_reason", rows(row("termination_reason", "RESIGNED"), row("termination_reason", "RESIGNED"), row("termination_reason", null)));
    }

    @Test
    void workforceWorksOutHeadcountTurnoverAndBreakdowns() {
        Workforce w = service(workforceData()).workforce(TENANT, FROM, TO, held("HR_REPORT_READ"));
        assertThat(w.opening()).isEqualTo(17);
        assertThat(w.closing()).isEqualTo(20);
        assertThat(w.joiners()).isEqualTo(6);
        assertThat(w.leavers()).isEqualTo(3);
        assertThat(w.turnoverPercent()).isEqualByComparingTo("16.2");                    // 3 / 18.5
        assertThat(w.annualisedTurnoverPercent()).isEqualByComparingTo("64.3");          // 16.2 * 365 / 92
        assertThat(w.byDepartment().stream().map(g -> g.label() + "=" + g.count()).toList()).containsExactly("Finance=12", "Operations=6", "No department=2");
        assertThat(w.leaversByReason().stream().map(g -> g.label() + "=" + g.count()).toList()).containsExactly("Resigned=2", "Not recorded=1");
        assertThat(w.byEmploymentType().stream().map(g -> g.label()).toList()).containsExactly("Permanent", "Fixed term");
        assertThat(w.age().stream().map(g -> g.label() + "=" + g.count()).toList()).contains("Not recorded=2", "35 to 44=12", "25 to 34=6");
    }

    @Test
    void workforceHidesGenderRaceAndDisabilityFromThoseWithoutSensitiveAccess() {
        Workforce w = service(workforceData()).workforce(TENANT, FROM, TO, held("HR_REPORT_READ", "HR_READ"));
        assertThat(w.demographicsShown()).isFalse();
        assertThat(w.gender()).isEmpty();
        assertThat(w.race()).isEmpty();
        assertThat(w.disability()).isEmpty();
    }

    @Test
    void workforceHidesSmallDemographicGroupsEvenForThoseWithAccess() {
        Workforce w = service(workforceData()).workforce(TENANT, FROM, TO, held("HR_REPORT_READ", "HR_SENSITIVE_READ"));
        assertThat(w.demographicsShown()).isTrue();
        // 12 female and 8 male: both shown. Race is 18 African and 2 white: the white group is too small, and hiding it alone would let it be
        // worked out from the headcount, so both are hidden. Disability is the same.
        assertThat(w.gender().stream().map(g -> g.label() + "=" + g.count()).toList()).containsExactly("Female=12", "Male=8");
        assertThat(w.race().stream().map(g -> g.label() + "=" + g.count()).toList()).containsExactly("African=null", "White=null");
        assertThat(w.race().stream().allMatch(g -> g.hidden())).isTrue();
        assertThat(w.disability().stream().allMatch(g -> g.hidden() && g.count() == null)).isTrue();
    }

    @Test
    void anEmptyWorkforceHasNoTurnoverRate() {
        Workforce w = service(new FakeJdbc()).workforce(TENANT, FROM, TO, held("HR_REPORT_READ"));
        assertThat(w.closing()).isEqualTo(0);
        assertThat(w.turnoverPercent()).isNull();
        assertThat(w.annualisedTurnoverPercent()).isNull();
    }

    @Test
    void aBadPeriodIsRefused() {
        ReportService s = service(new FakeJdbc());
        boolean refused = false;
        try { s.workforce(TENANT, TO, FROM, held()); } catch (IllegalArgumentException e) { refused = true; }
        assertThat(refused).isTrue();
        refused = false;
        try { s.compliance(TENANT, FROM, FROM.plusDays(400), held()); } catch (IllegalArgumentException e) { refused = true; }
        assertThat(refused).isTrue();
    }

    // ── Time and leave ───────────────────────────────────────────────────────────────────────────

    private FakeJdbc timeData() {
        return new FakeJdbc()
                .on("GROUP BY leave_type", rows(row("leave_type", "ANNUAL", "n", 4L, "days", new BigDecimal("12.0")), row("leave_type", "SICK", "n", 2L, "days", new BigDecimal("3.0"))))
                .on("FILTER (WHERE created_at", rows(row("n", 5L, "stale", 2L)))
                .on("leave_type = 'SICK'", rows(row("days", new BigDecimal("3.0"), "people", 2L)))
                .on("GROUP BY c.kind", rows(row("label", "OVERTIME", "n", 3L, "amount", new BigDecimal("900.00"), "hours", new BigDecimal("4.00"))))
                .on("JOIN hr_employees e", rows(row("label", "Finance", "n", 3L, "amount", new BigDecimal("900.00"), "hours", new BigDecimal("4.00"))))
                .on("status = 'PENDING'", rows(row("n", 1L)));
    }

    @Test
    void timeAddsUpLeaveAttendanceAndOvertime() {
        List<RegisterRow> register = List.of(
                new RegisterRow(UUID.randomUUID(), "E1", "Ann", "Finance", 40, 3, 2, 5, 9600, 120, 1),
                new RegisterRow(UUID.randomUUID(), "E2", "Bob", "Finance", 38, 1, 4, 0, 9000, 60, 0),
                new RegisterRow(UUID.randomUUID(), "E3", "Cy", "Ops", 42, 0, 0, 0, 10080, 0, 0));
        TimeReport t = service(timeData(), (tn, f, to, h, a) -> register).time(TENANT, FROM, TO, held("HR_REPORT_READ", "HR_COMPENSATION_READ"), UUID.randomUUID());
        assertThat(t.leave()).hasSize(2);
        assertThat(t.leavePending()).isEqualTo(5L);
        assertThat(t.leavePendingStale()).isEqualTo(2L);
        assertThat(t.sickDays()).isEqualByComparingTo("3.0");
        assertThat(t.sickEmployees()).isEqualTo(2L);
        assertThat(t.attendance().employees()).isEqualTo(3);
        assertThat(t.attendance().absentDays()).isEqualTo(6);
        assertThat(t.attendance().lateDays()).isEqualTo(4);
        assertThat(t.attendance().workedHours()).isEqualByComparingTo("478.0");
        assertThat(t.attendance().overtimeHours()).isEqualByComparingTo("3.0");
        assertThat(t.attendance().mostAbsent().get(0).employeeName()).isEqualTo("Bob");
        assertThat(t.attendance().mostAbsent()).hasSize(2);                            // Cy was never absent
        assertThat(t.overtimeByKind().get(0).hours()).isEqualByComparingTo("4.0");
        assertThat(t.overtimeByKind().get(0).amount()).isEqualByComparingTo("900.00");
        assertThat(t.overtimePending()).isEqualTo(1L);
    }

    @Test
    void overtimeAmountsNeedACompensationRight() {
        TimeReport t = service(timeData()).time(TENANT, FROM, TO, held("HR_REPORT_READ"), UUID.randomUUID());
        assertThat(t.overtimeByKind().get(0).amount()).isNull();
        assertThat(t.overtimeByDepartment().get(0).amount()).isNull();
        assertThat(t.overtimeByKind().get(0).hours()).isEqualByComparingTo("4.0");
    }

    @Test
    void attendanceIsLeftOutWhenThePersonMayNotSeeTheRegister() {
        TimeReport t = service(timeData(), (tn, f, to, h, a) -> { throw new AccessDeniedException("no"); }).time(TENANT, FROM, TO, held("HR_REPORT_READ"), null);
        assertThat(t.attendance()).isNull();
        assertThat(t.attendanceNote()).contains("do not have access");
        assertThat(t.leave()).hasSize(2);
    }

    @Test
    void attendanceIsLeftOutForAPeriodTheRegisterRefuses() {
        TimeReport t = service(timeData(), (tn, f, to, h, a) -> { throw new IllegalArgumentException("Choose a period of 62 days or less."); }).time(TENANT, FROM, TO, held("HR_REPORT_READ"), null);
        assertThat(t.attendance()).isNull();
        assertThat(t.attendanceNote()).contains("62 days");
    }

    // ── Payroll ──────────────────────────────────────────────────────────────────────────────────

    private FakeJdbc payrollData() {
        Map<String, Object> done = row("pay_run_number", "PR-1", "period_start", LocalDate.of(2026, 8, 1), "period_end", LocalDate.of(2026, 8, 31), "pay_date", LocalDate.of(2026, 8, 31),
                "status", "FINALISED", "n", 10L, "gross", new BigDecimal("100000.00"), "overtime", new BigDecimal("2000.00"), "paye", new BigDecimal("18000.00"),
                "uif_ee", new BigDecimal("1000.00"), "uif_er", new BigDecimal("1000.00"), "sdl", new BigDecimal("1000.00"), "net", new BigDecimal("81000.00"));
        Map<String, Object> draft = new HashMap<>(done);
        draft.put("pay_run_number", "PR-2"); draft.put("status", "PREPARED"); draft.put("pay_date", LocalDate.of(2026, 9, 30));
        return new FakeJdbc()
                .on("FROM hr_pay_runs r LEFT JOIN", rows(done, draft))
                .on("COUNT(DISTINCT p.employee_id)", rows(row("label", "Finance", "n", 6L, "amount", new BigDecimal("70000.00")), row("label", "Operations", "n", 4L, "amount", new BigDecimal("32000.00"))))
                .on("FROM hr_emp201", rows(
                        row("period_start", LocalDate.of(2026, 8, 1), "period_end", LocalDate.of(2026, 8, 31), "due_date", LocalDate.of(2026, 9, 7), "total_paye", new BigDecimal("18000.00"),
                                "total_uif", new BigDecimal("2000.00"), "total_sdl", new BigDecimal("1000.00"), "total_payable", new BigDecimal("21000.00"), "status", "SUBMITTED", "payment_ref", null),
                        row("period_start", LocalDate.of(2026, 7, 1), "period_end", LocalDate.of(2026, 7, 31), "due_date", LocalDate.of(2026, 8, 7), "total_paye", new BigDecimal("17000.00"),
                                "total_uif", new BigDecimal("2000.00"), "total_sdl", new BigDecimal("1000.00"), "total_payable", new BigDecimal("20000.00"), "status", "PAID", "payment_ref", "REF1")));
    }

    @Test
    void payrollIsRefusedWithoutACompensationRight() {
        boolean refused = false;
        try { service(payrollData()).payroll(TENANT, FROM, TO, held("HR_REPORT_READ", "HR_MANAGE")); } catch (AccessDeniedException e) { refused = true; }
        assertThat(refused).isTrue();
    }

    @Test
    void payrollTotalsCountOnlyFinishedRunsButListsTheOthers() {
        PayrollReport p = service(payrollData()).payroll(TENANT, FROM, TO, held("HR_REPORT_READ", "HR_COMPENSATION_READ"));
        assertThat(p.runs()).hasSize(2);
        assertThat(p.runs().get(0).counted()).isTrue();
        assertThat(p.runs().get(1).counted()).isFalse();
        assertThat(p.runs().get(0).employerCost()).isEqualByComparingTo("102000.00");
        assertThat(p.totals().runs()).isEqualTo(1);
        assertThat(p.totals().gross()).isEqualByComparingTo("100000.00");
        assertThat(p.totals().paye()).isEqualByComparingTo("18000.00");
        assertThat(p.totals().employerCost()).isEqualByComparingTo("102000.00");
        assertThat(p.totals().overtime()).isEqualByComparingTo("2000.00");
        assertThat(p.totals().net()).isEqualByComparingTo("81000.00");
        assertThat(p.byDepartment()).hasSize(2);
    }

    @Test
    void anEmp201IsOverdueWhenUnpaidAndPastDue() {
        PayrollReport p = service(payrollData()).payroll(TENANT, FROM, TO, held("HR_COMPENSATION_READ"));
        assertThat(p.emp201().get(0).overdue()).isTrue();     // submitted, due 7 September, today 22 October
        assertThat(p.emp201().get(1).overdue()).isFalse();    // paid
    }

    // ── Compliance ───────────────────────────────────────────────────────────────────────────────

    private FakeJdbc complianceData() {
        return new FakeJdbc()
                .on("SELECT COUNT(*) AS n FROM hr_employees", rows(row("n", 2L)))
                .on("SELECT employee_number, first_name", rows(row("employee_number", "E1", "first_name", "Ann", "last_name", "Dlamini"), row("employee_number", "E2", "first_name", "Bob", "last_name", "Nkosi")))
                .on("COUNT(*) AS n, AVG(", rows(row("n", 3L, "avg_days", new BigDecimal("41.26"))))
                .on("SELECT case_type, category", rows(row("case_type", "DISCIPLINARY", "category", "ABSENTEEISM"), row("case_type", "DISCIPLINARY", "category", "LATENESS"), row("case_type", "GRIEVANCE", "category", "WORKLOAD")))
                .on("SELECT outcome", rows(row("outcome", "WRITTEN_WARNING")))
                .on("FROM hr_leave_requests", rows(row("n", 4L)))
                .on("FROM hr_perf_plans", rows(row("n", 1L)))
                .on("FROM hr_er_cases", rows(row("n", 7L)));
    }

    @Test
    void complianceListsNamesOnlyForThoseWhoSeeEveryEmployee() {
        Compliance withNames = service(complianceData()).compliance(TENANT, FROM, TO, held("HR_REPORT_READ", "HR_READ"));
        assertThat(withNames.checks().get(0).count()).isEqualTo(2L);
        assertThat(withNames.checks().get(0).people()).containsExactly("E1 Ann Dlamini", "E2 Bob Nkosi");
        Compliance without = service(complianceData()).compliance(TENANT, FROM, TO, held("HR_REPORT_READ"));
        assertThat(without.checks().get(0).count()).isEqualTo(2L);
        assertThat(without.checks().get(0).people()).isEmpty();
    }

    @Test
    void employeeRelationsNeedsAnEmployeeRelationsRight() {
        assertThat(service(complianceData()).compliance(TENANT, FROM, TO, held("HR_REPORT_READ", "HR_READ")).employeeRelations()).isNull();
        assertThat(service(complianceData()).compliance(TENANT, FROM, TO, held("HR_REPORT_READ", "HR_ER_READ")).employeeRelations()).isNotNull();
    }

    @Test
    void confidentialCasesAreLeftOutUnlessThePersonMaySeeSensitiveRecords() {
        FakeJdbc jdbc = complianceData();
        ErSummary er = service(jdbc).compliance(TENANT, FROM, TO, held("HR_REPORT_READ", "HR_ER_READ")).employeeRelations();
        assertThat(jdbc.ran("confidential = false")).isTrue();
        assertThat(er.confidentialLeftOut()).isEqualTo(7L);
        FakeJdbc all = complianceData();
        ErSummary full = service(all).compliance(TENANT, FROM, TO, held("HR_REPORT_READ", "HR_ER_READ", "HR_SENSITIVE_READ")).employeeRelations();
        assertThat(all.ran("confidential = false")).isFalse();
        assertThat(full.confidentialLeftOut()).isEqualTo(0L);
    }

    @Test
    void employeeRelationsSummarisesTheCases() {
        ErSummary er = service(complianceData()).compliance(TENANT, FROM, TO, held("HR_REPORT_READ", "HR_ER_READ", "HR_SENSITIVE_READ")).employeeRelations();
        assertThat(er.closed()).isEqualTo(3L);
        assertThat(er.averageDaysToClose()).isEqualByComparingTo("41.3");
        assertThat(er.byType().stream().map(g -> g.label() + "=" + g.count()).toList()).containsExactly("Disciplinary=2", "Grievance=1");
        assertThat(er.outcomes().get(0).label()).isEqualTo("Written warning");
    }
}
