package za.co.handyflow.platform.accounting.application.internal;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.internal.ProfitabilityRules.Cell;
import za.co.handyflow.platform.accounting.dto.CostCentreDtos.CostCentre;
import za.co.handyflow.platform.accounting.dto.ProfitabilityDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/** Profitability by cost centre: guards, sites, vehicles, projects, branches (finance F28). Posted and reversed journals only. */
@Service
public class ProfitabilityService {

    private static final String SIGNED = "CASE WHEN a.account_type = 'INCOME' THEN l.credit_amount - l.debit_amount ELSE l.debit_amount - l.credit_amount END";
    private final JdbcTemplate jdbc;
    private final CostCentreService centres;

    public ProfitabilityService(JdbcTemplate jdbc, CostCentreService centres) {
        this.jdbc = jdbc;
        this.centres = centres;
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "PROFITABILITY_REFUSED"); }

    private Map<UUID, UUID> parents(TenantId tenantId) {
        Map<UUID, UUID> m = new HashMap<>();
        jdbc.query("SELECT id, parent_id FROM acc_cost_centres WHERE tenant_id = ?", rs -> { m.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)); }, tenantId.getValue());
        return m;
    }

    private static Row toRow(ProfitabilityRules.Row r, CostCentre c) {
        BigDecimal exp = r.expenseTotal(), profit = r.profit();
        return new Row(c == null ? null : c.id(), c == null ? null : c.code(), c == null ? null : c.name(), c == null ? null : c.kind(),
                r.income(), r.expenses(), exp, profit, ProfitabilityRules.marginPercent(r.income(), profit));
    }

    @Transactional(readOnly = true)
    public View view(TenantId tenantId, LocalDate from, LocalDate to, String kind, boolean rollUp) {
        if (from == null || to == null || to.isBefore(from)) throw bad("Choose a period that ends on or after it starts.");
        if (kind != null && !kind.isBlank() && !CostCentreRules.KINDS.contains(kind)) throw bad("Unknown kind.");
        List<CostCentre> all = centres.list(tenantId);
        List<CostCentre> shown = all.stream().filter(c -> kind == null || kind.isBlank() || kind.equals(c.kind())).toList();
        List<Cell> cells = new ArrayList<>();
        jdbc.query("""
                SELECT l.cost_centre_id, a.account_type, a.account_subtype, SUM(%s)
                  FROM acc_journal_lines l
                  JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                  JOIN acc_accounts a ON a.id = l.account_id
                 WHERE e.tenant_id = ? AND l.tenant_id = ? AND a.tenant_id = ? AND a.account_type IN ('INCOME', 'EXPENSE')
                   AND e.status IN ('POSTED', 'REVERSED') AND e.deleted_at IS NULL AND e.entry_date BETWEEN ? AND ?
                 GROUP BY l.cost_centre_id, a.account_type, a.account_subtype""".formatted(SIGNED),
                rs -> { cells.add(new Cell(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getBigDecimal(4))); },
                tenantId.getValue(), tenantId.getValue(), tenantId.getValue(), Date.valueOf(from), Date.valueOf(to));
        var v = ProfitabilityRules.build(cells, shown.stream().map(CostCentre::id).toList(), parents(tenantId), rollUp);
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) rows.add(toRow(v.rows().get(i), shown.get(i)));
        // Most profitable first, so loss-makers sit at the bottom.
        rows.sort(Comparator.comparing(Row::profit).reversed().thenComparing(Row::code));
        return new View(from, to, rollUp, kind, rows, toRow(v.unallocated(), null), toRow(v.total(), null), v.subtypes());
    }

    /** Month by month for one cost centre and everything beneath it. */
    @Transactional(readOnly = true)
    public Trend trend(TenantId tenantId, UUID centreId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw bad("Choose a period that ends on or after it starts.");
        CostCentre c = centres.list(tenantId).stream().filter(x -> x.id().equals(centreId)).findFirst()
                .orElseThrow(() -> new HandyFlowException("Cost centre not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        Set<UUID> ids = CostCentreRules.withDescendants(centreId, parents(tenantId));
        String marks = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>(List.of(tenantId.getValue(), tenantId.getValue(), tenantId.getValue()));
        args.addAll(ids);
        args.add(Date.valueOf(from));
        args.add(Date.valueOf(to));
        Map<LocalDate, List<Cell>> byMonth = new TreeMap<>();
        jdbc.query("""
                SELECT date_trunc('month', e.entry_date)::date, a.account_type, a.account_subtype, SUM(%s)
                  FROM acc_journal_lines l
                  JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                  JOIN acc_accounts a ON a.id = l.account_id
                 WHERE e.tenant_id = ? AND l.tenant_id = ? AND a.tenant_id = ? AND l.cost_centre_id IN (%s)
                   AND a.account_type IN ('INCOME', 'EXPENSE') AND e.status IN ('POSTED', 'REVERSED') AND e.deleted_at IS NULL
                   AND e.entry_date BETWEEN ? AND ?
                 GROUP BY 1, a.account_type, a.account_subtype ORDER BY 1""".formatted(SIGNED, marks),
                rs -> { byMonth.computeIfAbsent(rs.getDate(1).toLocalDate(), k -> new ArrayList<>()).add(new Cell(centreId, rs.getString(2), rs.getString(3), rs.getBigDecimal(4))); },
                args.toArray());
        List<Month> out = new ArrayList<>();
        // Every month in the period appears, so a quiet month shows as zero rather than a gap.
        for (LocalDate m = from.withDayOfMonth(1); !m.isAfter(to); m = m.plusMonths(1)) {
            var r = ProfitabilityRules.build(byMonth.getOrDefault(m, List.of()), List.of(centreId), new HashMap<>(), false).rows().get(0);
            out.add(new Month(m, r.income(), r.expenses(), r.expenseTotal(), r.profit()));
        }
        return new Trend(centreId, c.code(), c.name(), out);
    }
}
