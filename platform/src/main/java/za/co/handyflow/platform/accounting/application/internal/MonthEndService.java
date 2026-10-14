package za.co.handyflow.platform.accounting.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.internal.MonthEndRules.BankState;
import za.co.handyflow.platform.accounting.application.internal.MonthEndRules.ManualStep;
import za.co.handyflow.platform.accounting.dto.CashFlowResponse;
import za.co.handyflow.platform.accounting.dto.MonthEndChecklistResponse;
import za.co.handyflow.platform.accounting.dto.PeriodLockResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Month-end close checklist and the cash flow statement (finance patch F13). The checklist reads the ledger, the posting
 * queue, bank sign-offs and VAT periods; closing the month is the existing lock date, refused until the checklist is clear.
 */
@Service
@RequiredArgsConstructor
public class MonthEndService {

    private final JdbcTemplate jdbc;
    private final PeriodLockService periodLock;

    @Transactional(readOnly = true)
    public MonthEndChecklistResponse checklist(TenantId tenantId, LocalDate periodEnd) {
        UUID t = tenantId.getValue();
        Date end = Date.valueOf(periodEnd);
        LocalDate locked = periodLock.lockedThrough(tenantId);

        List<MonthEndRules.Item> items = new ArrayList<>();
        items.add(MonthEndRules.countItem("DRAFTS", "No draft journals", count("""
                SELECT count(*) FROM acc_journal_entries WHERE tenant_id = ? AND status = 'DRAFT' AND deleted_at IS NULL AND entry_date <= ?""", t, end),
                "{n} draft journal(s) dated on or before " + periodEnd + ": post or discard them", true));
        items.add(MonthEndRules.countItem("POSTING", "No posting problems waiting", count("""
                SELECT count(*) FROM acc_posting_failures WHERE tenant_id = ? AND status = 'OPEN'""", t),
                "{n} posting problem(s) open: retry or dismiss them under Posting problems", true));

        List<BankState> banks = jdbc.query("""
                SELECT b.bank_name || ' - ' || b.account_name,
                       (SELECT max(r.period_end) FROM acc_bank_reconciliations r
                         WHERE r.bank_account_id = b.id AND r.status = 'SIGNED_OFF'),
                       EXISTS (SELECT 1 FROM acc_bank_transactions x WHERE x.bank_account_id = b.id AND x.transaction_date <= ?)
                  FROM acc_bank_accounts b
                 WHERE b.tenant_id = ? AND b.active = true AND b.deleted_at IS NULL ORDER BY 1""",
                (rs, n) -> new BankState(rs.getString(1), local(rs.getDate(2)), rs.getBoolean(3)), end, t);
        items.add(MonthEndRules.bankItem(periodEnd, banks));

        BigDecimal[] tb = jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.debit_amount), 0), COALESCE(SUM(l.credit_amount), 0)
                  FROM acc_journal_lines l JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                 WHERE e.tenant_id = ? AND e.status IN ('POSTED', 'REVERSED') AND e.deleted_at IS NULL AND e.entry_date <= ?""",
                (rs, n) -> new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2)}, t, end);
        items.add(MonthEndRules.trialBalanceItem(tb[0], tb[1]));

        items.add(MonthEndRules.countItem("VAT", "VAT periods ended by this date are closed", count("""
                SELECT count(*) FROM acc_vat_periods WHERE tenant_id = ? AND period_end <= ? AND status = 'OPEN'""", t, end),
                "{n} VAT period(s) ended by " + periodEnd + " are still open (ignore if you do not submit VAT for this month)", false));

        Map<String, Object[]> done = new HashMap<>();
        jdbc.query("SELECT step_key, note, confirmed_by, confirmed_at FROM acc_close_confirmations WHERE tenant_id = ? AND period_end = ?",
                rs -> { done.put(rs.getString(1), new Object[]{rs.getString(2), rs.getObject(3, UUID.class), rs.getTimestamp(4)}); }, t, end);
        List<UUID> by = new ArrayList<>();
        List<java.time.Instant> at = new ArrayList<>();
        int firstManual = items.size();
        for (ManualStep s : MonthEndRules.MANUAL_STEPS) {
            Object[] c = done.get(s.key());
            items.add(MonthEndRules.manualItem(s, c != null, c != null && c[0] != null ? (String) c[0] : "Confirmed"));
            by.add(c != null ? (UUID) c[1] : null);
            at.add(c != null && c[2] != null ? ((java.sql.Timestamp) c[2]).toInstant() : null);
        }
        items.add(MonthEndRules.lockItem(periodEnd, locked));

        String problem = MonthEndRules.closeProblem(periodEnd, LocalDate.now(), locked, items);
        List<MonthEndChecklistResponse.Item> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            MonthEndRules.Item it = items.get(i);
            int m = i - firstManual;
            boolean manual = m >= 0 && m < by.size();
            out.add(new MonthEndChecklistResponse.Item(it.key(), it.title(), it.kind(), it.status(), it.detail(), it.blocking(),
                    manual ? by.get(m) : null, manual ? at.get(m) : null));
        }
        return new MonthEndChecklistResponse(periodEnd, locked, problem == null, problem, out);
    }

    @Transactional
    public MonthEndChecklistResponse confirm(TenantId tenantId, LocalDate periodEnd, String stepKey, String note, UUID by) {
        String problem = MonthEndRules.confirmProblem(stepKey, periodEnd, periodLock.lockedThrough(tenantId));
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "CLOSE_STEP_REFUSED");
        String trimmed = note == null || note.isBlank() ? null : note.trim();
        if (trimmed != null && trimmed.length() > 500) trimmed = trimmed.substring(0, 500);
        jdbc.update("""
                INSERT INTO acc_close_confirmations (tenant_id, period_end, step_key, note, confirmed_by)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, period_end, step_key)
                DO UPDATE SET note = EXCLUDED.note, confirmed_by = EXCLUDED.confirmed_by, confirmed_at = now()""",
                tenantId.getValue(), Date.valueOf(periodEnd), stepKey, trimmed, by);
        return checklist(tenantId, periodEnd);
    }

    @Transactional
    public MonthEndChecklistResponse unconfirm(TenantId tenantId, LocalDate periodEnd, String stepKey) {
        String problem = MonthEndRules.confirmProblem(stepKey, periodEnd, periodLock.lockedThrough(tenantId));
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "CLOSE_STEP_REFUSED");
        jdbc.update("DELETE FROM acc_close_confirmations WHERE tenant_id = ? AND period_end = ? AND step_key = ?",
                tenantId.getValue(), Date.valueOf(periodEnd), stepKey);
        return checklist(tenantId, periodEnd);
    }

    /** Closes the month: refused until the checklist is clear, then the books' lock date moves to the month end. */
    @Transactional
    public PeriodLockResponse close(TenantId tenantId, LocalDate periodEnd, String note, UUID by) {
        MonthEndChecklistResponse c = checklist(tenantId, periodEnd);
        if (c.closeProblem() != null) throw new HandyFlowException(c.closeProblem(), HttpStatus.BAD_REQUEST, "CLOSE_REFUSED");
        String n = "Month-end close" + (note == null || note.isBlank() ? "" : ": " + note.trim());
        return periodLock.lock(tenantId, periodEnd, n.length() > 500 ? n.substring(0, 500) : n, by);
    }

    @Transactional(readOnly = true)
    public CashFlowResponse cashFlow(TenantId tenantId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from))
            throw new HandyFlowException("Choose a start date on or before the end date", HttpStatus.BAD_REQUEST, "BAD_PERIOD");
        UUID t = tenantId.getValue();
        List<CashFlowRules.Move> moves = jdbc.query("""
                SELECT a.account_code, a.account_name, a.account_type, a.account_subtype,
                       COALESCE(SUM(l.debit_amount - l.credit_amount), 0)
                  FROM acc_accounts a
                  JOIN acc_journal_lines l ON l.account_id = a.id
                  JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                 WHERE a.tenant_id = ? AND e.tenant_id = ? AND e.status IN ('POSTED', 'REVERSED') AND e.deleted_at IS NULL
                   AND e.entry_date BETWEEN ? AND ?
                 GROUP BY a.account_code, a.account_name, a.account_type, a.account_subtype
                 ORDER BY a.account_code""",
                (rs, n) -> new CashFlowRules.Move(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5)),
                t, t, Date.valueOf(from), Date.valueOf(to));
        BigDecimal opening = jdbc.queryForObject("""
                SELECT COALESCE((SELECT SUM(a.opening_balance) FROM acc_accounts a
                                  WHERE a.tenant_id = ? AND a.account_type = 'ASSET' AND a.account_subtype IN ('CASH', 'BANK')), 0)
                     + COALESCE((SELECT SUM(l.debit_amount - l.credit_amount)
                                   FROM acc_journal_lines l
                                   JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                                   JOIN acc_accounts a ON a.id = l.account_id
                                  WHERE e.tenant_id = ? AND a.tenant_id = ? AND a.account_type = 'ASSET' AND a.account_subtype IN ('CASH', 'BANK')
                                    AND e.status IN ('POSTED', 'REVERSED') AND e.deleted_at IS NULL AND e.entry_date < ?), 0)""",
                BigDecimal.class, t, t, t, Date.valueOf(from));
        CashFlowRules.Statement s = CashFlowRules.build(moves, opening);
        return new CashFlowResponse(from, to, s.netProfit(),
                lines(s.operating()), s.operatingTotal(), lines(s.investing()), s.investingTotal(),
                lines(s.financing()), s.financingTotal(), s.netChange(), s.openingCash(), s.closingCash(),
                s.difference(), CashFlowRules.agrees(s));
    }

    private static List<CashFlowResponse.Line> lines(List<CashFlowRules.Line> in) {
        return in.stream().map(l -> new CashFlowResponse.Line(l.label(), l.amount())).toList();
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static LocalDate local(Date d) { return d == null ? null : d.toLocalDate(); }
}
