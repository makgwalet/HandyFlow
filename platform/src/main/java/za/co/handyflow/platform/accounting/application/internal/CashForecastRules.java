package za.co.handyflow.platform.accounting.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure rules for the 13-week cash forecast. Money in is what customers owe on the day we expect them to pay;
 * money out is what suppliers are owed on the day it is due, plus items the business adds (payroll, VAT, loans).
 * Everything here is an estimate; the assumptions are shown with the numbers.
 */
public final class CashForecastRules {
    private CashForecastRules() {}

    public static final int WEEKS = 13;

    public record Assumptions(int customerDelayDays, int overdueReceiptInDays, int overduePayInDays, int collectPercent) {
        public static Assumptions defaults() { return new Assumptions(0, 14, 7, 100); }
    }

    /** amount is always positive; direction is IN or OUT. */
    public record Item(LocalDate date, BigDecimal amount, String direction, String label, String source) {}

    public record Week(int index, LocalDate start, LocalDate end, BigDecimal opening, BigDecimal in, BigDecimal out, BigDecimal closing) {}

    public record Forecast(List<Week> weeks, BigDecimal lowest, LocalDate lowestWeekStart, Integer firstNegativeWeek,
                           BigDecimal laterIn, BigDecimal laterOut, List<String> warnings) {}

    public static String assumptionsProblem(Assumptions a) {
        if (a.customerDelayDays() < 0 || a.customerDelayDays() > 120) return "Customer delay must be 0 to 120 days.";
        if (a.overdueReceiptInDays() < 0 || a.overdueReceiptInDays() > 365) return "Overdue receipt days must be 0 to 365.";
        if (a.overduePayInDays() < 0 || a.overduePayInDays() > 120) return "Overdue payment days must be 0 to 120.";
        if (a.collectPercent() < 0 || a.collectPercent() > 100) return "Collection percentage must be 0 to 100.";
        return null;
    }

    public static String itemProblem(LocalDate date, BigDecimal amount, String direction, String label) {
        if (label == null || label.isBlank()) return "Say what the item is.";
        if (label.trim().length() > 200) return "The description is too long.";
        if (date == null) return "Choose a date.";
        if (amount == null || amount.signum() <= 0) return "The amount must be more than zero.";
        if (!"IN".equals(direction) && !"OUT".equals(direction)) return "Direction must be IN or OUT.";
        return null;
    }

    /** When we expect a customer to pay: after the due date plus the usual delay, or soon if already overdue. */
    public static LocalDate expectedReceipt(LocalDate due, LocalDate today, Assumptions a) {
        if (due == null) return today.plusDays(30L + a.customerDelayDays());
        LocalDate d = due.plusDays(a.customerDelayDays());
        return d.isBefore(today) ? today.plusDays(a.overdueReceiptInDays()) : d;
    }

    /** When we expect to pay a supplier: on the due date, or soon if already overdue. */
    public static LocalDate expectedPayment(LocalDate due, LocalDate today, Assumptions a) {
        if (due == null || due.isBefore(today)) return today.plusDays(a.overduePayInDays());
        return due;
    }

    public static BigDecimal collected(BigDecimal balance, Assumptions a) {
        return balance.multiply(BigDecimal.valueOf(a.collectPercent())).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    public static Forecast build(BigDecimal opening, LocalDate today, List<Item> items, BigDecimal lowBalance) {
        BigDecimal[] in = new BigDecimal[WEEKS], out = new BigDecimal[WEEKS];
        for (int i = 0; i < WEEKS; i++) { in[i] = BigDecimal.ZERO; out[i] = BigDecimal.ZERO; }
        BigDecimal laterIn = BigDecimal.ZERO, laterOut = BigDecimal.ZERO;
        for (Item it : items) {
            long days = ChronoUnit.DAYS.between(today, it.date());
            int w = days < 0 ? 0 : (int) (days / 7);
            boolean isIn = "IN".equals(it.direction());
            if (w >= WEEKS) {
                if (isIn) laterIn = laterIn.add(it.amount()); else laterOut = laterOut.add(it.amount());
            } else if (isIn) in[w] = in[w].add(it.amount());
            else out[w] = out[w].add(it.amount());
        }
        List<Week> weeks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        BigDecimal bal = opening, lowest = opening;
        LocalDate lowestAt = today;
        Integer firstNeg = null;
        for (int i = 0; i < WEEKS; i++) {
            BigDecimal open = bal;
            bal = bal.add(in[i]).subtract(out[i]);
            weeks.add(new Week(i, today.plusDays(7L * i), today.plusDays(7L * i + 6), open, in[i], out[i], bal));
            if (bal.compareTo(lowest) < 0) { lowest = bal; lowestAt = today.plusDays(7L * i); }
            if (firstNeg == null && bal.signum() < 0) firstNeg = i;
        }
        if (firstNeg != null) warnings.add("Cash is expected to run out in week " + (firstNeg + 1) + " (from " + today.plusDays(7L * firstNeg) + ").");
        else if (lowBalance != null && lowest.compareTo(lowBalance) < 0)
            warnings.add("Cash is expected to fall below the R " + lowBalance.setScale(2, RoundingMode.HALF_UP) + " minimum, to R " + lowest.setScale(2, RoundingMode.HALF_UP) + ", in the week of " + lowestAt + ".");
        if (laterOut.compareTo(laterIn) > 0) warnings.add("More is due out than in after the 13 weeks (R " + laterOut.subtract(laterIn).setScale(2, RoundingMode.HALF_UP) + " net).");
        return new Forecast(weeks, lowest, lowestAt, firstNeg, laterIn, laterOut, warnings);
    }
}
