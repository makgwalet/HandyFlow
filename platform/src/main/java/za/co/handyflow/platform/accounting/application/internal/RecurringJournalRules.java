package za.co.handyflow.platform.accounting.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure rules for recurring journals, accruals and prepayments (finance F17). No framework, no database.
 * Three kinds of schedule: RECURRING (the same journal every period), ACCRUAL (a journal at period end that is
 * reversed on the first day of the next period) and PREPAYMENT (a cost spread evenly: Dr expense, Cr prepaid asset).
 */
public final class RecurringJournalRules {

    private RecurringJournalRules() {}

    public static final Set<String> KINDS = Set.of("RECURRING", "ACCRUAL", "PREPAYMENT");
    public static final Set<String> FREQUENCIES = Set.of("MONTHLY", "QUARTERLY", "ANNUALLY");
    /** Most occurrences made in one run, so a schedule left idle for years cannot flood the drafts. */
    public static final int MAX_CATCH_UP = 12;

    public record Line(UUID accountId, String description, BigDecimal debit, BigDecimal credit) {}

    public static int monthsPer(String frequency) {
        return switch (frequency) {
            case "MONTHLY" -> 1;
            case "QUARTERLY" -> 3;
            case "ANNUALLY" -> 12;
            default -> throw new IllegalArgumentException("Unknown frequency " + frequency);
        };
    }

    /** Date of occurrence {@code index} (0 = start). Anchored on the start so a 31st does not drift to the 28th. */
    public static LocalDate runDate(LocalDate start, String frequency, int index) {
        return start.plusMonths((long) monthsPer(frequency) * index);
    }

    /** Equal parts rounded to cents; the last part takes the remainder so the parts add up exactly. */
    public static List<BigDecimal> spread(BigDecimal total, int parts) {
        if (parts < 1) throw new IllegalArgumentException("parts must be at least 1");
        BigDecimal each = total.divide(BigDecimal.valueOf(parts), 2, RoundingMode.HALF_UP);
        List<BigDecimal> out = new ArrayList<>();
        BigDecimal used = BigDecimal.ZERO;
        for (int i = 0; i < parts - 1; i++) { out.add(each); used = used.add(each); }
        out.add(total.subtract(used));
        return out;
    }

    /** Release of a prepayment for one period: Dr expense, Cr prepaid asset. */
    public static List<Line> prepaymentLines(UUID expenseAccount, UUID prepaidAccount, BigDecimal amount, String text) {
        return List.of(new Line(expenseAccount, text, amount, BigDecimal.ZERO),
                new Line(prepaidAccount, text, BigDecimal.ZERO, amount));
    }

    /** The reversing journal of an accrual: debits and credits swapped. */
    public static List<Line> reversal(List<Line> lines) {
        return lines.stream().map(l -> new Line(l.accountId(), l.description(), l.credit(), l.debit())).toList();
    }

    /** An accrual is reversed on the first day of the month after the accrual date. */
    public static LocalDate reversalDate(LocalDate accrualDate) {
        return accrualDate.withDayOfMonth(1).plusMonths(1);
    }

    /** Null when the lines make a sound journal. */
    public static String linesProblem(List<Line> lines) {
        if (lines == null || lines.size() < 2) return "A journal needs at least two lines.";
        BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            if (l.accountId() == null) return "Line " + (i + 1) + " has no account.";
            BigDecimal d = l.debit() == null ? BigDecimal.ZERO : l.debit();
            BigDecimal c = l.credit() == null ? BigDecimal.ZERO : l.credit();
            if (d.signum() < 0 || c.signum() < 0) return "Line " + (i + 1) + " has a negative amount.";
            if (d.signum() > 0 && c.signum() > 0) return "Line " + (i + 1) + " has both a debit and a credit.";
            if (d.signum() == 0 && c.signum() == 0) return "Line " + (i + 1) + " has no amount.";
            debit = debit.add(d);
            credit = credit.add(c);
        }
        if (debit.compareTo(credit) != 0) return "Debits (" + debit + ") do not equal credits (" + credit + ").";
        return null;
    }

    /** Null when the schedule may be created. {@code lines} is used for RECURRING and ACCRUAL only. */
    public static String createProblem(String name, String kind, String frequency, LocalDate start, LocalDate end,
                                       Integer runs, List<Line> lines, BigDecimal total,
                                       UUID expenseAccount, UUID prepaidAccount) {
        if (name == null || name.isBlank()) return "Give the schedule a name.";
        if (kind == null || !KINDS.contains(kind)) return "Choose the kind of schedule.";
        if (frequency == null || !FREQUENCIES.contains(frequency)) return "Choose how often it runs.";
        if (start == null) return "Choose the first date.";
        if (end != null && end.isBefore(start)) return "The end date is before the first date.";
        if (runs != null && runs < 1) return "The number of runs must be at least 1.";
        if ("PREPAYMENT".equals(kind)) {
            if (total == null || total.signum() <= 0) return "Enter the amount being spread.";
            if (runs == null || runs < 2) return "Spread a prepayment over at least 2 periods.";
            if (expenseAccount == null || prepaidAccount == null) return "Choose the expense and prepaid accounts.";
            if (expenseAccount.equals(prepaidAccount)) return "The expense and prepaid accounts must differ.";
            return null;
        }
        return linesProblem(lines);
    }

    /** True once no further occurrence should be made. */
    public static boolean finished(int runsDone, Integer runsTotal, LocalDate nextRun, LocalDate end) {
        if (runsTotal != null && runsDone >= runsTotal) return true;
        return end != null && nextRun.isAfter(end);
    }

    /** Index of the occurrences due on or before {@code today}, at most MAX_CATCH_UP, from {@code runsDone}. */
    public static List<Integer> dueIndexes(LocalDate start, String frequency, int runsDone, Integer runsTotal,
                                           LocalDate end, LocalDate today) {
        List<Integer> out = new ArrayList<>();
        int i = runsDone;
        while (out.size() < MAX_CATCH_UP) {
            LocalDate d = runDate(start, frequency, i);
            if (finished(i, runsTotal, d, end) || d.isAfter(today)) break;
            out.add(i);
            i++;
        }
        return out;
    }

    /** Upcoming dates for a preview, from {@code runsDone}. */
    public static List<LocalDate> upcoming(LocalDate start, String frequency, int runsDone, Integer runsTotal,
                                           LocalDate end, int count) {
        List<LocalDate> out = new ArrayList<>();
        int i = runsDone;
        while (out.size() < count) {
            LocalDate d = runDate(start, frequency, i);
            if (finished(i, runsTotal, d, end)) break;
            out.add(d);
            i++;
        }
        return out;
    }
}
