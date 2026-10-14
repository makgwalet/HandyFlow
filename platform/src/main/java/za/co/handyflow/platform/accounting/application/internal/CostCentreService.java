package za.co.handyflow.platform.accounting.application.internal;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.internal.CostCentreRules.Cell;
import za.co.handyflow.platform.accounting.dto.CostCentreDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/** Cost centres, departments, branches and projects, and profit and loss by cost centre (finance F24). */
@Service
public class CostCentreService {

    static final int DETAIL_LIMIT = 500;

    private final JdbcTemplate jdbc;

    public CostCentreService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "COST_CENTRE_REFUSED"); }
    private static HandyFlowException missing() { return new HandyFlowException("Cost centre not found", HttpStatus.NOT_FOUND, "NOT_FOUND"); }

    @Transactional(readOnly = true)
    public List<CostCentre> list(TenantId tenantId) {
        return jdbc.query("""
                SELECT c.id, c.code, c.name, c.kind, c.parent_id, c.active,
                       (SELECT COUNT(*) FROM acc_journal_lines l WHERE l.cost_centre_id = c.id AND l.tenant_id = c.tenant_id)
                  FROM acc_cost_centres c WHERE c.tenant_id = ? ORDER BY c.kind, c.code""",
                (rs, n) -> new CostCentre(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, UUID.class), rs.getBoolean(6), rs.getLong(7)),
                tenantId.getValue());
    }

    private Map<UUID, UUID> parents(TenantId tenantId) {
        Map<UUID, UUID> m = new HashMap<>();
        jdbc.query("SELECT id, parent_id FROM acc_cost_centres WHERE tenant_id = ?",
                rs -> { m.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)); }, tenantId.getValue());
        return m;
    }

    private CostCentre one(TenantId tenantId, UUID id) {
        return list(tenantId).stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(CostCentreService::missing);
    }

    @Transactional
    public CostCentre create(TenantId tenantId, Save req, UUID by) {
        String problem = CostCentreRules.saveProblem(req.code(), req.name(), req.kind());
        if (problem == null) problem = CostCentreRules.parentProblem(null, req.parentId(), parents(tenantId));
        if (problem != null) throw bad(problem);
        if (codeTaken(tenantId, req.code().trim(), null)) throw bad("Another cost centre already uses the code " + req.code().trim().toUpperCase() + ".");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO acc_cost_centres (id, tenant_id, code, name, kind, parent_id, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, tenantId.getValue(), req.code().trim().toUpperCase(), req.name().trim(), req.kind(), req.parentId(), by);
        return one(tenantId, id);
    }

    @Transactional
    public CostCentre update(TenantId tenantId, UUID id, Save req) {
        one(tenantId, id);
        String problem = CostCentreRules.saveProblem(req.code(), req.name(), req.kind());
        if (problem == null) problem = CostCentreRules.parentProblem(id, req.parentId(), parents(tenantId));
        if (problem != null) throw bad(problem);
        if (codeTaken(tenantId, req.code().trim(), id)) throw bad("Another cost centre already uses the code " + req.code().trim().toUpperCase() + ".");
        jdbc.update("UPDATE acc_cost_centres SET code = ?, name = ?, kind = ?, parent_id = ? WHERE id = ? AND tenant_id = ?",
                req.code().trim().toUpperCase(), req.name().trim(), req.kind(), req.parentId(), id, tenantId.getValue());
        return one(tenantId, id);
    }

    @Transactional
    public CostCentre setActive(TenantId tenantId, UUID id, boolean active) {
        one(tenantId, id);
        jdbc.update("UPDATE acc_cost_centres SET active = ? WHERE id = ? AND tenant_id = ?", active, id, tenantId.getValue());
        return one(tenantId, id);
    }

    private boolean codeTaken(TenantId tenantId, String code, UUID except) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM acc_cost_centres WHERE tenant_id = ? AND upper(code) = upper(?) AND (? IS NULL OR id <> ?)",
                Integer.class, tenantId.getValue(), code, except, except);
        return n != null && n > 0;
    }

    /** Throws if any id is not an active cost centre of this tenant. Called when a journal is drafted. */
    @Transactional(readOnly = true)
    public void requireUsable(TenantId tenantId, Collection<UUID> ids) {
        Set<UUID> wanted = new HashSet<>(ids);
        wanted.remove(null);
        if (wanted.isEmpty()) return;
        List<Object> args = new ArrayList<>();
        args.add(tenantId.getValue());
        args.addAll(wanted);
        Set<UUID> ok = new HashSet<>(jdbc.queryForList("SELECT id FROM acc_cost_centres WHERE tenant_id = ? AND active = TRUE AND id IN (" + marks(wanted.size()) + ")",
                UUID.class, args.toArray()));
        if (!ok.containsAll(wanted)) throw new IllegalArgumentException("A journal line names a cost centre that does not exist or is switched off");
    }

    private static Object[] args(TenantId tenantId, Collection<UUID> ids, Object... tail) {
        List<Object> a = new ArrayList<>();
        a.add(tenantId.getValue());
        a.add(tenantId.getValue());
        a.addAll(ids);
        a.addAll(Arrays.asList(tail));
        return a.toArray();
    }

    private static String marks(int n) { return String.join(",", Collections.nCopies(n, "?")); }

    // ── reports ──────────────────────────────────────────────────────────────

    /** Profit and loss with one column per cost centre (optionally limited to one kind), plus Unallocated. Posted and reversed journals only. */
    @Transactional(readOnly = true)
    public ByCentreReport profitAndLoss(TenantId tenantId, LocalDate from, LocalDate to, boolean rollUp, String kind) {
        if (from == null || to == null || to.isBefore(from)) throw bad("Choose a period that ends on or after it starts.");
        if (kind != null && !kind.isBlank() && !CostCentreRules.KINDS.contains(kind)) throw bad("Unknown kind.");
        List<CostCentre> all = list(tenantId);
        Map<UUID, UUID> parentOf = parents(tenantId);
        List<CostCentre> shown = all.stream().filter(c -> kind == null || kind.isBlank() || kind.equals(c.kind())).toList();
        List<UUID> shownIds = shown.stream().map(CostCentre::id).toList();

        List<Cell> cells = new ArrayList<>();
        jdbc.query("""
                SELECT a.id, a.account_code, a.account_name, a.account_type, l.cost_centre_id,
                       SUM(CASE WHEN a.account_type = 'INCOME' THEN l.credit_amount - l.debit_amount ELSE l.debit_amount - l.credit_amount END)
                  FROM acc_journal_lines l
                  JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                  JOIN acc_accounts a ON a.id = l.account_id
                 WHERE e.tenant_id = ? AND l.tenant_id = ? AND a.tenant_id = ? AND a.account_type IN ('INCOME', 'EXPENSE')
                   AND e.status IN ('POSTED', 'REVERSED') AND e.deleted_at IS NULL AND e.entry_date BETWEEN ? AND ?
                 GROUP BY a.id, a.account_code, a.account_name, a.account_type, l.cost_centre_id""",
                rs -> {
                    UUID centre = rs.getObject(5, UUID.class);
                    // Lines in a centre of another kind than the one shown count as unallocated for this view, so totals still agree with the ledger.
                    if (centre != null && !shownIds.contains(centre) && !rollUp) centre = null;
                    if (centre != null && !shownIds.contains(centre) && rollUp) centre = nearestShown(centre, shownIds, parentOf);
                    cells.add(new Cell(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), centre, rs.getBigDecimal(6)));
                }, tenantId.getValue(), tenantId.getValue(), tenantId.getValue(), Date.valueOf(from), Date.valueOf(to));

        var p = CostCentreRules.pivot(cells, shownIds, parentOf, rollUp);
        List<Column> cols = new ArrayList<>();
        for (CostCentre c : shown) cols.add(new Column(c.id(), c.code(), c.name(), c.kind()));
        cols.add(new Column(null, "-", "Unallocated", null));
        return new ByCentreReport(from, to, rollUp, cols, rows(p.income()), rows(p.expenses()),
                p.incomeTotals(), p.expenseTotals(), p.resultTotals(), p.incomeTotal(), p.expenseTotal(), p.resultTotal());
    }

    /** The closest ancestor that is in the shown set, or null. */
    private static UUID nearestShown(UUID id, List<UUID> shown, Map<UUID, UUID> parentOf) {
        UUID cur = parentOf.get(id);
        int guard = 0;
        while (cur != null && guard++ < 20) {
            if (shown.contains(cur)) return cur;
            cur = parentOf.get(cur);
        }
        return null;
    }

    private static List<Row> rows(List<CostCentreRules.PivotRow> in) {
        return in.stream().map(r -> new Row(r.accountId(), r.code(), r.name(), r.type(), r.amounts(), r.total())).toList();
    }

    /** The lines behind one cost centre (and everything beneath it), newest first. */
    @Transactional(readOnly = true)
    public CentreDetail detail(TenantId tenantId, UUID id, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw bad("Choose a period that ends on or after it starts.");
        CostCentre c = one(tenantId, id);
        Set<UUID> ids = CostCentreRules.withDescendants(id, parents(tenantId));
        String in = marks(ids.size());
        List<CentreLine> lines = jdbc.query("""
                SELECT e.entry_date, e.entry_number, e.description, a.account_code, a.account_name, l.description,
                       CASE WHEN a.account_type = 'INCOME' THEN l.credit_amount - l.debit_amount ELSE l.debit_amount - l.credit_amount END
                  FROM acc_journal_lines l
                  JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                  JOIN acc_accounts a ON a.id = l.account_id
                 WHERE e.tenant_id = ? AND l.tenant_id = ? AND l.cost_centre_id IN (%s)
                   AND a.account_type IN ('INCOME', 'EXPENSE')
                   AND e.status IN ('POSTED', 'REVERSED') AND e.deleted_at IS NULL AND e.entry_date BETWEEN ? AND ?
                 ORDER BY e.entry_date DESC, e.entry_number DESC, l.sort_order LIMIT ?""".formatted(in),
                (rs, n) -> new CentreLine(rs.getDate(1).toLocalDate(), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getBigDecimal(7)),
                args(tenantId, ids, Date.valueOf(from), Date.valueOf(to), DETAIL_LIMIT + 1));
        boolean truncated = lines.size() > DETAIL_LIMIT;
        if (truncated) lines = lines.subList(0, DETAIL_LIMIT);
        // Totals are computed in SQL so they stay right when the list is cut.
        BigDecimal[] t = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN a.account_type = 'INCOME' THEN l.credit_amount - l.debit_amount END), 0),
                       COALESCE(SUM(CASE WHEN a.account_type = 'EXPENSE' THEN l.debit_amount - l.credit_amount END), 0)
                  FROM acc_journal_lines l
                  JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                  JOIN acc_accounts a ON a.id = l.account_id
                 WHERE e.tenant_id = ? AND l.tenant_id = ? AND l.cost_centre_id IN (%s)
                   AND a.account_type IN ('INCOME', 'EXPENSE')
                   AND e.status IN ('POSTED', 'REVERSED') AND e.deleted_at IS NULL AND e.entry_date BETWEEN ? AND ?""".formatted(in),
                (rs, n) -> new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2)},
                args(tenantId, ids, Date.valueOf(from), Date.valueOf(to)));
        return new CentreDetail(c, from, to, t[0], t[1], t[0].subtract(t[1]), lines, truncated);
    }
}
