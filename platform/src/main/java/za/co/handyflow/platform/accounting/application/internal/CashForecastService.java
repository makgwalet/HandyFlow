package za.co.handyflow.platform.accounting.application.internal;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.internal.CashForecastRules.Assumptions;
import za.co.handyflow.platform.accounting.dto.BudgetDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 13-week cash forecast (finance F21). Opening cash comes from the ledger; money in is open customer invoices,
 * money out is approved supplier bills, plus items added by hand. The invoice and bill tables are read directly,
 * so this class is the one place that knows their columns.
 */
@Service
public class CashForecastService {

    private final JdbcTemplate jdbc;

    public CashForecastService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "FORECAST_REFUSED"); }

    @Transactional(readOnly = true)
    public ForecastResponse forecast(TenantId tenantId, Assumptions a, BigDecimal lowBalance) {
        String problem = CashForecastRules.assumptionsProblem(a);
        if (problem != null) throw bad(problem);
        UUID t = tenantId.getValue();
        LocalDate today = LocalDate.now();
        BigDecimal opening = jdbc.queryForObject("""
                SELECT COALESCE((SELECT SUM(x.opening_balance) FROM acc_accounts x
                                  WHERE x.tenant_id = ? AND x.account_type = 'ASSET' AND x.account_subtype IN ('CASH', 'BANK')), 0)
                     + COALESCE((SELECT SUM(l.debit_amount - l.credit_amount)
                                   FROM acc_journal_lines l
                                   JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                                   JOIN acc_accounts x ON x.id = l.account_id
                                  WHERE e.tenant_id = ? AND x.tenant_id = ? AND x.account_type = 'ASSET' AND x.account_subtype IN ('CASH', 'BANK')
                                    AND e.status IN ('POSTED', 'REVERSED') AND e.deleted_at IS NULL AND e.entry_date <= ?), 0)""",
                BigDecimal.class, t, t, t, Date.valueOf(today));

        List<CashForecastRules.Item> items = new ArrayList<>();
        BigDecimal receivables = BigDecimal.ZERO, payables = BigDecimal.ZERO;

        // Customers: issued, part-paid or overdue invoices with something still owing.
        var inv = jdbc.queryForList("""
                SELECT invoice_number, due_date, total - amount_paid - credited_amount AS balance
                  FROM invoices
                 WHERE tenant_id = ? AND deleted_at IS NULL AND status IN ('ISSUED', 'PARTIALLY_PAID', 'OVERDUE')
                   AND total - amount_paid - credited_amount > 0""", t);
        for (var r : inv) {
            BigDecimal bal = (BigDecimal) r.get("balance");
            receivables = receivables.add(bal);
            BigDecimal got = CashForecastRules.collected(bal, a);
            if (got.signum() <= 0) continue;
            Date due = (Date) r.get("due_date");
            items.add(new CashForecastRules.Item(CashForecastRules.expectedReceipt(due == null ? null : due.toLocalDate(), today, a),
                    got, "IN", "Invoice " + r.get("invoice_number"), "INVOICE"));
        }

        // Suppliers: approved (or awaiting second approval) bills with something still owing.
        var bills = jdbc.queryForList("""
                SELECT supplier_name, due_date, total_amount - paid_amount - credited_amount AS balance
                  FROM ap_bills
                 WHERE tenant_id = ? AND deleted_at IS NULL AND status IN ('APPROVED', 'OVERDUE', 'SECOND_APPROVAL')
                   AND total_amount - paid_amount - credited_amount > 0""", t);
        for (var r : bills) {
            BigDecimal bal = (BigDecimal) r.get("balance");
            payables = payables.add(bal);
            Date due = (Date) r.get("due_date");
            items.add(new CashForecastRules.Item(CashForecastRules.expectedPayment(due == null ? null : due.toLocalDate(), today, a),
                    bal, "OUT", "Bill from " + r.get("supplier_name"), "BILL"));
        }

        for (ItemResponse m : manualItems(tenantId))
            items.add(new CashForecastRules.Item(m.date(), m.amount(), m.direction(), m.label(), "MANUAL"));

        var f = CashForecastRules.build(opening, today, items, lowBalance);
        items.sort(Comparator.comparing(CashForecastRules.Item::date));
        List<ForecastItem> detail = items.stream().limit(500)
                .map(i -> new ForecastItem(i.date(), i.amount(), i.direction(), i.label(), i.source())).toList();
        return new ForecastResponse(today, opening, a.customerDelayDays(), a.overdueReceiptInDays(), a.overduePayInDays(), a.collectPercent(), lowBalance,
                f.weeks().stream().map(w -> new ForecastWeek(w.index(), w.start(), w.end(), w.opening(), w.in(), w.out(), w.closing())).toList(),
                f.lowest(), f.lowestWeekStart(), f.firstNegativeWeek(), f.laterIn(), f.laterOut(), f.warnings(), detail, receivables, payables);
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> manualItems(TenantId tenantId) {
        return jdbc.query("""
                SELECT id, item_date, amount, direction, label FROM acc_forecast_items
                 WHERE tenant_id = ? AND active ORDER BY item_date, created_at""",
                (rs, n) -> new ItemResponse(rs.getObject(1, UUID.class), rs.getDate(2).toLocalDate(), rs.getBigDecimal(3), rs.getString(4), rs.getString(5)),
                tenantId.getValue());
    }

    @Transactional
    public ItemResponse addItem(TenantId tenantId, ItemRequest req, UUID by) {
        String problem = CashForecastRules.itemProblem(req.date(), req.amount(), req.direction(), req.label());
        if (problem != null) throw bad(problem);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO acc_forecast_items (id, tenant_id, item_date, amount, direction, label, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, tenantId.getValue(), Date.valueOf(req.date()), req.amount(), req.direction(), req.label().trim(), by);
        return new ItemResponse(id, req.date(), req.amount(), req.direction(), req.label().trim());
    }

    @Transactional
    public void removeItem(TenantId tenantId, UUID id) {
        int n = jdbc.update("UPDATE acc_forecast_items SET active = FALSE WHERE id = ? AND tenant_id = ? AND active", id, tenantId.getValue());
        if (n == 0) throw new HandyFlowException("Item not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}
