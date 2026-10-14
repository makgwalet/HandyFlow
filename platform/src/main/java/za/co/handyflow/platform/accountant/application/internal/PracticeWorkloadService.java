package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accountant.dto.PracticeServiceDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/** Staff workload, work in progress and what was billed against the time spent (finance F22). */
@Service
@RequiredArgsConstructor
public class PracticeWorkloadService {

    private final JdbcTemplate jdbc;
    private final ClientAccessService access;

    /** Everyone with all-clients access sees every person; anyone else sees only themselves. */
    @Transactional(readOnly = true)
    public List<WorkloadRow> workload(TenantId tenantId, LocalDate today) {
        UUID t = tenantId.getValue();
        UUID me;
        try { me = TenantContext.getCurrentUserId(); } catch (RuntimeException e) { me = null; }
        boolean all = access.seesAll();

        Map<UUID, int[]> counts = new LinkedHashMap<>();   // prepared, reviewed, partner
        jdbc.query("SELECT user_id, role, COUNT(DISTINCT client_id) FROM acc_client_assignments WHERE tenant_id = ? GROUP BY user_id, role", rs -> {
            int[] c = counts.computeIfAbsent(rs.getObject(1, UUID.class), k -> new int[3]);
            c["PREPARER".equals(rs.getString(2)) ? 0 : "REVIEWER".equals(rs.getString(2)) ? 1 : 2] = rs.getInt(3);
        }, t);
        Map<UUID, BigDecimal[]> time = new HashMap<>();    // hours30, unbilledHours, unbilledValue
        jdbc.query("""
                SELECT practitioner_id,
                       COALESCE(SUM(hours) FILTER (WHERE entry_date >= ?), 0),
                       COALESCE(SUM(hours) FILTER (WHERE status = 'UNBILLED' AND billable), 0),
                       COALESCE(SUM(hours * hourly_rate) FILTER (WHERE status = 'UNBILLED' AND billable), 0)
                  FROM acc_time_entries WHERE tenant_id = ? AND practitioner_id IS NOT NULL GROUP BY practitioner_id""", rs -> {
            time.put(rs.getObject(1, UUID.class), new BigDecimal[]{rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getBigDecimal(4)});
        }, Date.valueOf(today.minusDays(30)), t);
        Map<UUID, int[]> dl = new HashMap<>();             // next30, overdue
        jdbc.query("""
                SELECT a.user_id,
                       COUNT(*) FILTER (WHERE d.status = 'PENDING' AND d.adjusted_due_date BETWEEN ? AND ?),
                       COUNT(*) FILTER (WHERE d.status = 'OVERDUE' OR (d.status = 'PENDING' AND d.adjusted_due_date < ?))
                  FROM acc_client_assignments a JOIN acc_tax_deadlines d ON d.client_id = a.client_id
                 WHERE a.tenant_id = ? AND a.role = 'PREPARER' GROUP BY a.user_id""", rs -> {
            dl.put(rs.getObject(1, UUID.class), new int[]{rs.getInt(2), rs.getInt(3)});
        }, Date.valueOf(today), Date.valueOf(today.plusDays(30)), Date.valueOf(today), t);
        Map<UUID, Integer> runs = new HashMap<>();
        jdbc.query("""
                SELECT s.owner_user_id, COUNT(*) FROM acc_service_runs r JOIN acc_service_schedules s ON s.id = r.schedule_id
                 WHERE r.tenant_id = ? AND r.status = 'DUE' AND s.owner_user_id IS NOT NULL GROUP BY s.owner_user_id""",
                rs -> { runs.put(rs.getObject(1, UUID.class), rs.getInt(2)); }, t);

        Set<UUID> people = new LinkedHashSet<>(counts.keySet());
        people.addAll(time.keySet());
        people.addAll(runs.keySet());
        if (!all) { people.clear(); if (me != null) people.add(me); }
        Map<UUID, String> names = new HashMap<>();
        for (UUID u : people)
            names.put(u, jdbc.query("SELECT TRIM(first_name || ' ' || last_name) FROM users WHERE id = ? AND tenant_id = ?", (rs, n) -> rs.getString(1), u, t)
                    .stream().findFirst().orElse("Unknown user"));
        List<WorkloadRow> out = new ArrayList<>();
        for (UUID u : people) {
            int[] c = counts.getOrDefault(u, new int[3]);
            BigDecimal[] tm = time.getOrDefault(u, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            int[] d = dl.getOrDefault(u, new int[2]);
            out.add(new WorkloadRow(u, names.get(u), c[0], c[1], c[2], d[0], d[1], tm[0], tm[1], tm[2], runs.getOrDefault(u, 0)));
        }
        out.sort(Comparator.comparing((WorkloadRow r) -> r.deadlinesOverdue()).reversed().thenComparing(WorkloadRow::name));
        return out;
    }

    @Transactional(readOnly = true)
    public WipReport wip(TenantId tenantId, LocalDate asOf) {
        LocalDate today = asOf == null ? LocalDate.now() : asOf;
        Map<UUID, List<WipRules.Entry>> byClient = new LinkedHashMap<>();
        Map<UUID, String> names = new HashMap<>();
        jdbc.query("""
                SELECT e.client_id, c.trading_name, e.entry_date, e.hours, e.hourly_rate
                  FROM acc_time_entries e JOIN acc_clients c ON c.id = e.client_id
                 WHERE e.tenant_id = ? AND e.status = 'UNBILLED' AND e.billable AND e.entry_date <= ?""", rs -> {
            UUID id = rs.getObject(1, UUID.class);
            names.put(id, rs.getString(2));
            byClient.computeIfAbsent(id, k -> new ArrayList<>()).add(new WipRules.Entry(rs.getDate(3).toLocalDate(), rs.getBigDecimal(4), rs.getBigDecimal(5)));
        }, tenantId.getValue(), Date.valueOf(today));
        List<WipRow> rows = new ArrayList<>();
        for (var e : byClient.entrySet()) {
            WipRules.Aged a = WipRules.age(e.getValue(), today);
            rows.add(new WipRow(e.getKey(), names.get(e.getKey()), a.hours(), a.value(), a.current(), a.days31to60(), a.days61to90(), a.over90(), a.oldest()));
        }
        rows = new ArrayList<>(access.visible(tenantId, rows, WipRow::clientId));
        rows.sort(Comparator.comparing(WipRow::unbilledValue).reversed());
        BigDecimal total = rows.stream().map(WipRow::unbilledValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal old = rows.stream().map(r -> r.days61to90().add(r.over90())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WipReport(today, rows, total, old);
    }

    /** Hours and standard value of billable time in the period against fees invoiced in it, per client. */
    @Transactional(readOnly = true)
    public ProfitReport profitability(TenantId tenantId, LocalDate from, LocalDate to) {
        String problem = WipRules.periodProblem(from, to);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "BAD_PERIOD");
        UUID t = tenantId.getValue();
        Map<UUID, BigDecimal[]> m = new LinkedHashMap<>();   // hours, value, billed, budgetHours
        Map<UUID, String> names = new HashMap<>();
        jdbc.query("""
                SELECT e.client_id, c.trading_name, SUM(e.hours), SUM(e.hours * e.hourly_rate)
                  FROM acc_time_entries e JOIN acc_clients c ON c.id = e.client_id
                 WHERE e.tenant_id = ? AND e.billable AND e.status <> 'NON_BILLABLE' AND e.entry_date BETWEEN ? AND ?
                 GROUP BY e.client_id, c.trading_name""", rs -> {
            UUID id = rs.getObject(1, UUID.class);
            names.put(id, rs.getString(2));
            m.computeIfAbsent(id, k -> zero())[0] = rs.getBigDecimal(3);
            m.get(id)[1] = rs.getBigDecimal(4);
        }, t, Date.valueOf(from), Date.valueOf(to));
        jdbc.query("""
                SELECT f.client_id, c.trading_name, SUM(f.subtotal)
                  FROM acc_fee_notes f JOIN acc_clients c ON c.id = f.client_id
                 WHERE f.tenant_id = ? AND f.status <> 'WRITTEN_OFF' AND f.invoice_date BETWEEN ? AND ?
                 GROUP BY f.client_id, c.trading_name""", rs -> {
            UUID id = rs.getObject(1, UUID.class);
            names.putIfAbsent(id, rs.getString(2));
            m.computeIfAbsent(id, k -> zero())[2] = rs.getBigDecimal(3);
        }, t, Date.valueOf(from), Date.valueOf(to));
        jdbc.query("""
                SELECT r.client_id, c.trading_name, SUM(s.budget_hours)
                  FROM acc_service_runs r JOIN acc_service_schedules s ON s.id = r.schedule_id JOIN acc_clients c ON c.id = r.client_id
                 WHERE r.tenant_id = ? AND s.budget_hours IS NOT NULL AND r.run_date BETWEEN ? AND ?
                 GROUP BY r.client_id, c.trading_name""", rs -> {
            UUID id = rs.getObject(1, UUID.class);
            names.putIfAbsent(id, rs.getString(2));
            m.computeIfAbsent(id, k -> zero())[3] = rs.getBigDecimal(3);
        }, t, Date.valueOf(from), Date.valueOf(to));
        List<ProfitRow> rows = new ArrayList<>();
        for (var e : m.entrySet()) {
            BigDecimal[] v = e.getValue();
            rows.add(new ProfitRow(e.getKey(), names.get(e.getKey()), v[0], v[1], v[2], WipRules.realisation(v[2], v[1]),
                    v[3].signum() == 0 ? null : v[3], WipRules.budgetUsed(v[0], v[3])));
        }
        rows = new ArrayList<>(access.visible(tenantId, rows, ProfitRow::clientId));
        rows.sort(Comparator.comparing(ProfitRow::standardValue).reversed());
        return new ProfitReport(from, to, rows,
                rows.stream().map(ProfitRow::hours).reduce(BigDecimal.ZERO, BigDecimal::add),
                rows.stream().map(ProfitRow::standardValue).reduce(BigDecimal.ZERO, BigDecimal::add),
                rows.stream().map(ProfitRow::billed).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal[] zero() { return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}; }
}
