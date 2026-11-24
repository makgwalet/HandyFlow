package za.co.handyflow.platform.accounting.domain.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Works out a Trial Balance and a Balance Sheet as at a date (audit findings 1.1 and 1.2).
 *
 * <p>Balance-sheet accounts (asset, liability, equity) carry every posted movement up to the date, plus their opening
 * balance. Income and expense accounts count only from the start of the financial year; what earlier years earned
 * is rolled into Retained Earnings (3020), and this year's result into Current Year Earnings (3040). A journal on an
 * account that cannot be found is shown, never dropped, so the totals always agree with the journals.</p>
 *
 * <p>Pure: no database, no clock. Amounts are debit minus credit unless a name says otherwise.</p>
 */
public final class PositionReportRules {

    public static final String RETAINED_CODE = "3020";
    public static final String RETAINED_NAME = "Retained Earnings";
    public static final String CURRENT_CODE = "3040";
    public static final String CURRENT_NAME = "Current Year Earnings";
    public static final String UNKNOWN_TYPE = "UNKNOWN";

    private PositionReportRules() {}

    public record Acct(UUID id, String code, String name, String type, BigDecimal opening) {}

    public record Move(UUID accountId, LocalDate date, BigDecimal debit, BigDecimal credit) {}

    /** One account's totals. Balance-sheet accounts: all time to the date. Income and expense: this financial year only. */
    public record Row(String code, String name, String type, BigDecimal debit, BigDecimal credit) {
        public BigDecimal net() { return debit.subtract(credit); }
    }

    /** Net (debit minus credit) of income and expense from before the year started, and of the year itself. */
    public record Position(List<Row> rows, BigDecimal priorEarningsNet, BigDecimal currentEarningsNet) {}

    public record Line(String code, String name, BigDecimal amount) {}

    public record Sheet(List<Line> assets, List<Line> liabilities, List<Line> equity,
                        BigDecimal totalAssets, BigDecimal totalLiabilities, BigDecimal totalEquity) {}

    private static boolean isBalanceSheet(String type) {
        return "ASSET".equals(type) || "LIABILITY".equals(type) || "EQUITY".equals(type);
    }

    private static boolean isProfitAndLoss(String type) {
        return "INCOME".equals(type) || "EXPENSE".equals(type);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static final class Acc {
        final String code, name, type;
        BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
        Acc(String code, String name, String type) { this.code = code; this.name = name; this.type = type; }
    }

    public static Position compute(List<Acct> accounts, List<Move> moves, LocalDate periodStart, LocalDate asAt) {
        if (periodStart == null || asAt == null)
            throw new IllegalArgumentException("Choose the date the report is as at, and when the financial year starts.");
        if (periodStart.isAfter(asAt))
            throw new IllegalArgumentException("The financial year must start on or before the date the report is as at.");

        Map<UUID, Acc> byId = new LinkedHashMap<>();
        BigDecimal prior = BigDecimal.ZERO;
        for (Acct a : accounts) {
            Acc acc = new Acc(a.code(), a.name(), a.type());
            byId.put(a.id(), acc);
            BigDecimal opening = nz(a.opening());
            if (isProfitAndLoss(a.type())) {
                prior = prior.add(opening);
            } else if (opening.signum() > 0) {
                acc.debit = acc.debit.add(opening);
            } else if (opening.signum() < 0) {
                acc.credit = acc.credit.add(opening.negate());
            }
        }

        for (Move m : moves) {
            if (m.date().isAfter(asAt)) continue;
            Acc acc = byId.get(m.accountId());
            if (acc == null) {
                acc = new Acc("UNKNOWN", "Account not found (" + m.accountId() + ")", UNKNOWN_TYPE);
                byId.put(m.accountId(), acc);
            }
            BigDecimal d = nz(m.debit()), c = nz(m.credit());
            if (isProfitAndLoss(acc.type) && m.date().isBefore(periodStart)) {
                prior = prior.add(d.subtract(c));
            } else {
                acc.debit = acc.debit.add(d);
                acc.credit = acc.credit.add(c);
            }
        }

        List<Row> rows = new ArrayList<>();
        BigDecimal current = BigDecimal.ZERO;
        for (Acc acc : byId.values()) {
            if (isProfitAndLoss(acc.type)) current = current.add(acc.debit.subtract(acc.credit));
            if (acc.debit.signum() == 0 && acc.credit.signum() == 0) continue;
            rows.add(new Row(acc.code, acc.name, acc.type, acc.debit, acc.credit));
        }
        rows.sort(Comparator.comparing(Row::code).thenComparing(Row::name));
        return new Position(rows, prior, current);
    }

    /**
     * The Trial Balance rows. Earnings of earlier years are added to Retained Earnings so the debit and credit
     * totals agree; that one row is shown as a single net figure.
     */
    public static List<Row> trialBalance(Position p) {
        List<Row> rows = new ArrayList<>(p.rows());
        if (p.priorEarningsNet().signum() == 0) return rows;
        int at = -1;
        for (int i = 0; i < rows.size(); i++) if (RETAINED_CODE.equals(rows.get(i).code())) at = i;
        BigDecimal net = p.priorEarningsNet().add(at >= 0 ? rows.get(at).net() : BigDecimal.ZERO);
        Row merged = new Row(RETAINED_CODE, RETAINED_NAME, "EQUITY",
                net.signum() > 0 ? net : BigDecimal.ZERO, net.signum() < 0 ? net.negate() : BigDecimal.ZERO);
        if (at >= 0) rows.set(at, merged); else rows.add(merged);
        if (net.signum() == 0) rows.remove(merged);
        rows.sort(Comparator.comparing(Row::code).thenComparing(Row::name));
        return rows;
    }

    /** The Balance Sheet. Assets show debit minus credit; liabilities and equity show credit minus debit. */
    public static Sheet balanceSheet(Position p) {
        List<Line> assets = new ArrayList<>(), liabilities = new ArrayList<>();
        Map<String, String> equityNames = new HashMap<>();
        Map<String, BigDecimal> equity = new HashMap<>();
        for (Row r : p.rows()) {
            if ("ASSET".equals(r.type())) {
                add(assets, r.code(), r.name(), r.net());
            } else if ("LIABILITY".equals(r.type())) {
                add(liabilities, r.code(), r.name(), r.net().negate());
            } else if ("EQUITY".equals(r.type())) {
                equityNames.put(r.code(), r.name());
                equity.merge(r.code(), r.net().negate(), BigDecimal::add);
            }
        }
        equityNames.putIfAbsent(RETAINED_CODE, RETAINED_NAME);
        equityNames.putIfAbsent(CURRENT_CODE, CURRENT_NAME);
        equity.merge(RETAINED_CODE, p.priorEarningsNet().negate(), BigDecimal::add);
        equity.merge(CURRENT_CODE, p.currentEarningsNet().negate(), BigDecimal::add);

        List<Line> equityLines = new ArrayList<>();
        equity.forEach((code, amount) -> add(equityLines, code, equityNames.get(code), amount));
        for (List<Line> list : List.of(assets, liabilities, equityLines)) list.sort(Comparator.comparing(Line::code));
        return new Sheet(assets, liabilities, equityLines, total(assets), total(liabilities), total(equityLines));
    }

    private static void add(List<Line> lines, String code, String name, BigDecimal amount) {
        if (amount.signum() != 0) lines.add(new Line(code, name, amount));
    }

    private static BigDecimal total(List<Line> lines) {
        return lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
