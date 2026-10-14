package za.co.handyflow.platform.accounting.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Cash flow statement, indirect method, worked out from ledger movements (finance patch F13). Pure, so it is tested
 * without Spring.
 * <p>
 * Every posted journal balances, so the movement in cash is always the opposite of the movement in all other accounts.
 * The statement sorts those other movements into operating (profit, depreciation, debtors, creditors, VAT, stock),
 * investing (fixed assets) and financing (loans and equity), then checks the total against the real movement of the
 * cash and bank accounts. A difference means a journal that does not balance or an account that is not classified.
 */
public final class CashFlowRules {

    private CashFlowRules() {}

    /** An account's net movement in the period: debits minus credits. */
    public record Move(String code, String name, String type, String subtype, BigDecimal net) {}
    public record Line(String label, BigDecimal amount) {}
    public record Statement(
            BigDecimal netProfit, List<Line> operating, BigDecimal operatingTotal,
            List<Line> investing, BigDecimal investingTotal,
            List<Line> financing, BigDecimal financingTotal,
            BigDecimal netChange, BigDecimal openingCash, BigDecimal closingCash, BigDecimal difference) {}

    public static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    /** Cash and bank accounts: the money the statement explains. */
    public static boolean isCash(String type, String subtype) {
        return "ASSET".equals(type) && ("CASH".equals(subtype) || "BANK".equals(subtype));
    }

    public static boolean isProfit(String type) {
        return "INCOME".equals(type) || "EXPENSE".equals(type);
    }

    /** OPERATING, INVESTING or FINANCING for an account that is not cash and not profit and loss. */
    public static String section(String type, String subtype) {
        if ("ASSET".equals(type)) {
            return "FIXED_ASSET".equals(subtype) || "NON_CURRENT".equals(subtype) ? "INVESTING" : "OPERATING";
        }
        if ("LIABILITY".equals(type)) return "LOAN".equals(subtype) ? "FINANCING" : "OPERATING";
        return "FINANCING"; // equity
    }

    /**
     * @param moves        net movement of every account in the period
     * @param openingCash  cash and bank balance the day before the period starts
     */
    public static Statement build(List<Move> moves, BigDecimal openingCash) {
        BigDecimal profit = BigDecimal.ZERO, cashMove = BigDecimal.ZERO;
        List<Line> operating = new ArrayList<>(), investing = new ArrayList<>(), financing = new ArrayList<>();
        for (Move m : moves) {
            BigDecimal net = m.net() == null ? BigDecimal.ZERO : m.net();
            if (isCash(m.type(), m.subtype())) { cashMove = cashMove.add(net); continue; }
            BigDecimal effect = net.negate(); // a debit to a non-cash account used cash
            if (isProfit(m.type())) { profit = profit.add(effect); continue; }
            if (money(effect).signum() == 0) continue;
            String s = section(m.type(), m.subtype());
            Line line = new Line(label(m, effect), money(effect));
            ("INVESTING".equals(s) ? investing : "FINANCING".equals(s) ? financing : operating).add(line);
        }
        operating.add(0, new Line("Net profit for the period", money(profit)));
        BigDecimal op = total(operating), inv = total(investing), fin = total(financing);
        BigDecimal change = op.add(inv).add(fin);
        BigDecimal opening = money(openingCash);
        BigDecimal closing = money(opening.add(cashMove));
        return new Statement(money(profit), operating, op, investing, inv, financing, fin,
                change, opening, closing, money(opening.add(change).subtract(closing)));
    }

    /** True when the statement agrees with the real movement of the cash and bank accounts, to the cent. */
    public static boolean agrees(Statement s) {
        return s.difference().abs().compareTo(new BigDecimal("0.005")) < 0;
    }

    private static BigDecimal total(List<Line> lines) {
        BigDecimal t = BigDecimal.ZERO;
        for (Line l : lines) t = t.add(l.amount());
        return money(t);
    }

    private static String label(Move m, BigDecimal effect) {
        boolean up = effect.signum() > 0; // cash effect positive
        if ("DEPRECIATION".equals(m.subtype())) return m.name() + " (not paid in cash)";
        if ("ASSET".equals(m.type())) {
            return (up ? "Decrease in " : "Increase in ") + m.name();
        }
        if ("LIABILITY".equals(m.type())) {
            return (up ? "Increase in " : "Decrease in ") + m.name();
        }
        return (up ? "Money put in: " : "Money taken out: ") + m.name();
    }
}
