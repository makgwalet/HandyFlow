package za.co.handyflow.platform.accounting.application.internal;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.dto.BudgetDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/** Budgets by account and month, and budget against actual (finance F21). JDBC, like the other finance services. */
@Service
public class BudgetService {

    private final JdbcTemplate jdbc;

    public BudgetService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "BUDGET_REFUSED"); }
    private static HandyFlowException missing() { return new HandyFlowException("Budget not found", HttpStatus.NOT_FOUND, "NOT_FOUND"); }

    @Transactional(readOnly = true)
    public List<BudgetSummary> list(TenantId tenantId) {
        return jdbc.query("""
                SELECT b.id, b.name, b.start_date, b.status,
                       COALESCE(SUM(l.amount) FILTER (WHERE a.account_type = 'INCOME'), 0),
                       COALESCE(SUM(l.amount) FILTER (WHERE a.account_type = 'EXPENSE'), 0),
                       COUNT(l.account_id)
                  FROM acc_budgets b
                  LEFT JOIN acc_budget_lines l ON l.budget_id = b.id
                  LEFT JOIN acc_accounts a ON a.id = l.account_id
                 WHERE b.tenant_id = ?
                 GROUP BY b.id ORDER BY b.start_date DESC, b.created_at DESC""",
                (rs, n) -> summary(rs), tenantId.getValue());
    }

    private static BudgetSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        LocalDate start = rs.getDate(3).toLocalDate();
        return new BudgetSummary(rs.getObject(1, UUID.class), rs.getString(2), start, start.plusMonths(12).minusDays(1), rs.getString(4),
                rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getInt(7));
    }

    @Transactional
    public BudgetSummary create(TenantId tenantId, CreateBudget req, UUID by) {
        String problem = BudgetRules.createProblem(req.name(), req.startDate());
        if (problem != null) throw bad(problem);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO acc_budgets (id, tenant_id, name, start_date, created_by) VALUES (?, ?, ?, ?, ?)",
                id, tenantId.getValue(), req.name().trim(), Date.valueOf(req.startDate()), by);
        return one(tenantId, id);
    }

    private BudgetSummary one(TenantId tenantId, UUID id) {
        return list(tenantId).stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow(BudgetService::missing);
    }

    @Transactional(readOnly = true)
    public BudgetDetail detail(TenantId tenantId, UUID id) {
        BudgetSummary s = one(tenantId, id);
        Map<UUID, AccountLine> byAccount = new LinkedHashMap<>();
        jdbc.query("""
                SELECT a.id, a.account_code, a.account_name, a.account_type, l.month_index, l.amount
                  FROM acc_budget_lines l JOIN acc_accounts a ON a.id = l.account_id
                 WHERE l.budget_id = ? AND l.tenant_id = ?
                 ORDER BY a.account_type DESC, a.account_code, l.month_index""",
                rs -> {
                    UUID acc = rs.getObject(1, UUID.class);
                    AccountLine row = byAccount.computeIfAbsent(acc, k -> {
                        try {
                            List<BigDecimal> m = new ArrayList<>(Collections.nCopies(BudgetRules.MONTHS, BigDecimal.ZERO.setScale(2)));
                            return new AccountLine(acc, rs.getString(2), rs.getString(3), rs.getString(4), m, BigDecimal.ZERO);
                        } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
                    });
                    row.months().set(rs.getInt(5), rs.getBigDecimal(6));
                }, id, tenantId.getValue());
        List<AccountLine> rows = new ArrayList<>();
        for (AccountLine r : byAccount.values())
            rows.add(new AccountLine(r.accountId(), r.code(), r.name(), r.type(), r.months(), r.months().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
        return new BudgetDetail(s, rows);
    }

    private String statusOf(TenantId tenantId, UUID id) {
        List<String> r = jdbc.queryForList("SELECT status FROM acc_budgets WHERE id = ? AND tenant_id = ?", String.class, id, tenantId.getValue());
        if (r.isEmpty()) throw missing();
        return r.get(0);
    }

    /** Replaces every line of a draft budget. */
    @Transactional
    public BudgetDetail setLines(TenantId tenantId, UUID id, SetLines req) {
        String problem = BudgetRules.editProblem(statusOf(tenantId, id));
        if (problem != null) throw bad(problem);
        Map<UUID, String> types = new HashMap<>();
        if (req.lines() != null) {
            Set<UUID> ids = new HashSet<>();
            req.lines().forEach(l -> ids.add(l.accountId()));
            ids.remove(null);
            for (UUID a : ids) {
                List<String> t = jdbc.queryForList("SELECT account_type FROM acc_accounts WHERE id = ? AND tenant_id = ?", String.class, a, tenantId.getValue());
                if (!t.isEmpty()) types.put(a, t.get(0));
            }
        }
        List<BudgetRules.LineIn> in = req.lines() == null ? null
                : req.lines().stream().map(l -> new BudgetRules.LineIn(l.accountId(), l.month(), l.amount())).toList();
        problem = BudgetRules.linesProblem(in, types);
        if (problem != null) throw bad(problem);
        jdbc.update("DELETE FROM acc_budget_lines WHERE budget_id = ? AND tenant_id = ?", id, tenantId.getValue());
        for (BudgetRules.LineIn l : in)
            if (l.amount().signum() > 0)
                jdbc.update("INSERT INTO acc_budget_lines (budget_id, tenant_id, account_id, month_index, amount) VALUES (?, ?, ?, ?, ?)",
                        id, tenantId.getValue(), l.accountId(), l.month(), l.amount());
        return detail(tenantId, id);
    }

    /** Fills a draft budget from the 12 months before it starts, plus an uplift. Replaces what was there. */
    @Transactional
    public BudgetDetail copyFromActuals(TenantId tenantId, UUID id, CopyActuals req) {
        String problem = BudgetRules.editProblem(statusOf(tenantId, id));
        if (problem != null) throw bad(problem);
        problem = BudgetRules.upliftProblem(req == null ? null : req.upliftPercent());
        if (problem != null) throw bad(problem);
        LocalDate start = jdbc.queryForObject("SELECT start_date FROM acc_budgets WHERE id = ?", Date.class, id).toLocalDate();
        LocalDate priorStart = start.minusMonths(12);
        Map<UUID, BigDecimal[]> actual = new LinkedHashMap<>();
        jdbc.query("""
                SELECT a.id, a.account_type, e.entry_date, SUM(l.debit_amount - l.credit_amount)
                  FROM acc_journal_lines l
                  JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                  JOIN acc_accounts a ON a.id = l.account_id
                 WHERE e.tenant_id = ? AND a.tenant_id = ? AND a.account_type IN ('INCOME', 'EXPENSE')
                   AND e.status IN ('POSTED', 'REVERSED') AND e.deleted_at IS NULL
                   AND e.entry_date >= ? AND e.entry_date < ?
                 GROUP BY a.id, a.account_type, e.entry_date""",
                rs -> {
                    UUID acc = rs.getObject(1, UUID.class);
                    BigDecimal net = rs.getBigDecimal(4);
                    BigDecimal v = "INCOME".equals(rs.getString(2)) ? net.negate() : net;
                    int m = (int) java.time.temporal.ChronoUnit.MONTHS.between(priorStart, rs.getDate(3).toLocalDate().withDayOfMonth(1));
                    if (m < 0 || m >= BudgetRules.MONTHS) return;
                    BigDecimal[] arr = actual.computeIfAbsent(acc, k -> {
                        BigDecimal[] z = new BigDecimal[BudgetRules.MONTHS];
                        Arrays.fill(z, BigDecimal.ZERO);
                        return z;
                    });
                    arr[m] = arr[m].add(v);
                }, tenantId.getValue(), tenantId.getValue(), Date.valueOf(priorStart), Date.valueOf(start));
        jdbc.update("DELETE FROM acc_budget_lines WHERE budget_id = ? AND tenant_id = ?", id, tenantId.getValue());
        for (var e : actual.entrySet()) {
            List<BigDecimal> months = BudgetRules.fromActuals(Arrays.asList(e.getValue()), req == null ? null : req.upliftPercent());
            for (int m = 0; m < months.size(); m++)
                if (months.get(m).signum() > 0)
                    jdbc.update("INSERT INTO acc_budget_lines (budget_id, tenant_id, account_id, month_index, amount) VALUES (?, ?, ?, ?, ?)",
                            id, tenantId.getValue(), e.getKey(), m, months.get(m));
        }
        return detail(tenantId, id);
    }

    @Transactional
    public BudgetSummary activate(TenantId tenantId, UUID id, UUID by) {
        BudgetSummary s = one(tenantId, id);
        boolean rival = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM acc_budgets WHERE tenant_id = ? AND start_date = ? AND status = 'ACTIVE' AND id <> ?)",
                Boolean.class, tenantId.getValue(), Date.valueOf(s.startDate()), id));
        String problem = BudgetRules.activateProblem(s.status(), s.lines(), rival);
        if (problem != null) throw bad(problem);
        jdbc.update("UPDATE acc_budgets SET status = 'ACTIVE', activated_by = ?, activated_at = now() WHERE id = ? AND tenant_id = ?", by, id, tenantId.getValue());
        return one(tenantId, id);
    }

    @Transactional
    public BudgetSummary close(TenantId tenantId, UUID id) {
        String status = statusOf(tenantId, id);
        if (!"ACTIVE".equals(status)) throw bad("Only an active budget can be closed.");
        jdbc.update("UPDATE acc_budgets SET status = 'CLOSED', closed_at = now() WHERE id = ? AND tenant_id = ?", id, tenantId.getValue());
        return one(tenantId, id);
    }

    @Transactional
    public void deleteDraft(TenantId tenantId, UUID id) {
        if (!"DRAFT".equals(statusOf(tenantId, id))) throw bad("Only a draft budget can be deleted.");
        jdbc.update("DELETE FROM acc_budgets WHERE id = ? AND tenant_id = ?", id, tenantId.getValue());
    }

    /** Budget against actual for the months that have started, up to {@code asOf} (default today). */
    @Transactional(readOnly = true)
    public VarianceReport vsActual(TenantId tenantId, UUID id, LocalDate asOf) {
        BudgetSummary s = one(tenantId, id);
        if ("DRAFT".equals(s.status())) throw bad("Activate the budget before comparing it with actuals.");
        LocalDate today = asOf == null ? LocalDate.now() : asOf;
        int counted = 0;
        for (LocalDate m : BudgetRules.months(s.startDate())) if (!m.isAfter(today)) counted++;
        if (counted == 0) throw bad("This budget has not started yet.");
        LocalDate through = s.startDate().plusMonths(counted).minusDays(1);
        if (through.isAfter(today)) through = today;
        final int upto = counted;
        Map<UUID, BigDecimal> budgets = new HashMap<>();
        Map<UUID, String[]> info = new LinkedHashMap<>();
        jdbc.query("""
                SELECT a.id, a.account_code, a.account_name, a.account_type, SUM(l.amount) FILTER (WHERE l.month_index < ?)
                  FROM acc_budget_lines l JOIN acc_accounts a ON a.id = l.account_id
                 WHERE l.budget_id = ? AND l.tenant_id = ? GROUP BY a.id, a.account_code, a.account_name, a.account_type""",
                rs -> {
                    UUID a = rs.getObject(1, UUID.class);
                    budgets.put(a, rs.getBigDecimal(5) == null ? BigDecimal.ZERO : rs.getBigDecimal(5));
                    info.put(a, new String[]{rs.getString(2), rs.getString(3), rs.getString(4)});
                }, upto, id, tenantId.getValue());
        Map<UUID, BigDecimal> actuals = new HashMap<>();
        jdbc.query("""
                SELECT a.id, a.account_code, a.account_name, a.account_type, SUM(l.debit_amount - l.credit_amount)
                  FROM acc_journal_lines l
                  JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                  JOIN acc_accounts a ON a.id = l.account_id
                 WHERE e.tenant_id = ? AND a.tenant_id = ? AND a.account_type IN ('INCOME', 'EXPENSE')
                   AND e.status IN ('POSTED', 'REVERSED') AND e.deleted_at IS NULL AND e.entry_date BETWEEN ? AND ?
                 GROUP BY a.id, a.account_code, a.account_name, a.account_type""",
                rs -> {
                    UUID a = rs.getObject(1, UUID.class);
                    BigDecimal net = rs.getBigDecimal(5);
                    actuals.put(a, "INCOME".equals(rs.getString(4)) ? net.negate() : net);
                    info.putIfAbsent(a, new String[]{rs.getString(2), rs.getString(3), rs.getString(4)});
                }, tenantId.getValue(), tenantId.getValue(), Date.valueOf(s.startDate()), Date.valueOf(through));
        List<VarianceRow> rows = new ArrayList<>();
        BigDecimal bi = BigDecimal.ZERO, ai = BigDecimal.ZERO, be = BigDecimal.ZERO, ae = BigDecimal.ZERO;
        for (var e : info.entrySet()) {
            BigDecimal b = budgets.getOrDefault(e.getKey(), BigDecimal.ZERO), a = actuals.getOrDefault(e.getKey(), BigDecimal.ZERO);
            String type = e.getValue()[2];
            var v = BudgetRules.variance(type, b, a);
            rows.add(new VarianceRow(e.getKey(), e.getValue()[0], e.getValue()[1], type, b, a, v.amount(), v.percent(), v.favourable()));
            if ("INCOME".equals(type)) { bi = bi.add(b); ai = ai.add(a); } else { be = be.add(b); ae = ae.add(a); }
        }
        rows.sort(Comparator.comparing((VarianceRow r) -> r.type()).reversed().thenComparing(VarianceRow::code));
        return new VarianceReport(id, s.name(), s.startDate(), through, counted, rows, bi, ai, be, ae, bi.subtract(be), ai.subtract(ae));
    }
}
