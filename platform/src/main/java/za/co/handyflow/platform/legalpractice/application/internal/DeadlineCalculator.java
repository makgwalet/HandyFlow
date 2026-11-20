package za.co.handyflow.platform.legalpractice.application.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Works out a due date from a trigger date and a rule. Pure: no database, no clock, so every rule can be tested.
 * <p>
 * The first day (the trigger day) is never counted and the last day is. COURT_DAYS skip weekends, public holidays, court
 * closures and the rule's recess window; CALENDAR_DAYS count every day except those in the recess window; MONTHS and
 * YEARS move to the same day of the month or year (clamped to the end of a shorter month). The result of a calendar,
 * month or year period can then roll on to the next open day, depending on the rule's rollover: NONE, SKIP_SUNDAY_AND_HOLIDAYS
 * (the Interpretation Act rule) or SKIP_NON_COURT_DAYS. A period counted BEFORE an event rolls back, never forward.
 */
public final class DeadlineCalculator {

    public enum Unit { COURT_DAYS, CALENDAR_DAYS, MONTHS, YEARS }
    public enum Direction { AFTER, BEFORE }
    public enum Rollover { NONE, SKIP_SUNDAY_AND_HOLIDAYS, SKIP_NON_COURT_DAYS }
    public enum SkipReason { PUBLIC_HOLIDAY, COURT_CLOSURE, RECESS, WEEKEND }

    public static final int MAX_AMOUNT = 3660;
    public static final int MAX_MONTHS = 360;
    public static final int MAX_YEARS = 30;
    private static final int MAX_STEPS = 20_000;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

    /** Recess window runs from {@code recessStart} to {@code recessEnd} each year, both days included; it may span New Year. */
    public record Rule(int amount, Unit unit, Direction direction, Rollover rollover, MonthDay recessStart, MonthDay recessEnd) {}

    /** What the firm and the law say is not a normal working day, apart from weekends. */
    public interface Calendar {
        /** Name of the public holiday (statutory or proclaimed) on this date, or null. */
        String publicHoliday(LocalDate d);
        /** Reason the courts are closed on this date although it is not a public holiday, or null. */
        String courtClosure(LocalDate d);
    }

    public record Skipped(LocalDate date, SkipReason reason, String detail) {}

    public record Result(LocalDate date, List<Skipped> skipped) {
        /** One line per skipped day; a run of recess days is one line. */
        public List<String> explain() {
            List<String> out = new ArrayList<>();
            int i = 0;
            while (i < skipped.size()) {
                Skipped s = skipped.get(i);
                if (s.reason() == SkipReason.RECESS) {
                    int j = i;
                    while (j + 1 < skipped.size() && skipped.get(j + 1).reason() == SkipReason.RECESS
                            && Math.abs(skipped.get(j + 1).date().toEpochDay() - skipped.get(j).date().toEpochDay()) == 1) j++;
                    int n = j - i + 1;
                    out.add(n == 1 ? FMT.format(s.date()) + ": court recess"
                            : FMT.format(s.date()) + " to " + FMT.format(skipped.get(j).date()) + ": court recess (" + n + " days)");
                    i = j + 1;
                } else {
                    String why = switch (s.reason()) {
                        case WEEKEND -> "weekend";
                        case PUBLIC_HOLIDAY -> "public holiday" + (s.detail() == null ? "" : " (" + s.detail() + ")");
                        case COURT_CLOSURE -> "court closed" + (s.detail() == null ? "" : " (" + s.detail() + ")");
                        case RECESS -> "court recess";
                    };
                    out.add(FMT.format(s.date()) + ": " + why);
                    i++;
                }
            }
            return out;
        }
    }

    private DeadlineCalculator() {}

    public static void validate(Rule r) {
        if (r == null || r.unit() == null || r.direction() == null || r.rollover() == null) throw new IllegalArgumentException("The rule is incomplete");
        int max = switch (r.unit()) { case MONTHS -> MAX_MONTHS; case YEARS -> MAX_YEARS; default -> MAX_AMOUNT; };
        if (r.amount() < 1 || r.amount() > max) throw new IllegalArgumentException("The period must be between 1 and " + max + " " + r.unit().name().toLowerCase(Locale.ENGLISH).replace('_', ' '));
        if ((r.recessStart() == null) != (r.recessEnd() == null)) throw new IllegalArgumentException("Give both ends of the recess window, or neither");
    }

    public static Result compute(LocalDate trigger, Rule rule, Calendar cal) {
        if (trigger == null) throw new IllegalArgumentException("The trigger date is required");
        validate(rule);
        int step = rule.direction() == Direction.AFTER ? 1 : -1;
        List<Skipped> skipped = new ArrayList<>();
        LocalDate d = trigger;
        switch (rule.unit()) {
            case COURT_DAYS -> {
                int n = 0, guard = 0;
                while (n < rule.amount()) {
                    d = d.plusDays(step);
                    guard(++guard);
                    Skipped s = nonCourtDay(d, rule, cal);
                    if (s == null) n++; else skipped.add(s);
                }
            }
            case CALENDAR_DAYS -> {
                int n = 0, guard = 0;
                while (n < rule.amount()) {
                    d = d.plusDays(step);
                    guard(++guard);
                    if (inRecess(d, rule)) skipped.add(new Skipped(d, SkipReason.RECESS, null)); else n++;
                }
                d = roll(d, step, rule, cal, skipped);
            }
            case MONTHS -> d = roll(trigger.plusMonths((long) step * rule.amount()), step, rule, cal, skipped);
            case YEARS -> d = roll(trigger.plusYears((long) step * rule.amount()), step, rule, cal, skipped);
        }
        return new Result(d, List.copyOf(skipped));
    }

    private static LocalDate roll(LocalDate d, int step, Rule rule, Calendar cal, List<Skipped> skipped) {
        if (rule.rollover() == Rollover.NONE) return d;
        int guard = 0;
        while (true) {
            Skipped s = rule.rollover() == Rollover.SKIP_SUNDAY_AND_HOLIDAYS ? sundayOrHoliday(d, cal) : nonCourtDay(d, rule, cal);
            if (s == null) return d;
            skipped.add(s);
            d = d.plusDays(step);
            guard(++guard);
        }
    }

    private static Skipped sundayOrHoliday(LocalDate d, Calendar cal) {
        if (d.getDayOfWeek() == DayOfWeek.SUNDAY) return new Skipped(d, SkipReason.WEEKEND, null);
        String h = cal.publicHoliday(d);
        return h == null ? null : new Skipped(d, SkipReason.PUBLIC_HOLIDAY, h);
    }

    private static Skipped nonCourtDay(LocalDate d, Rule rule, Calendar cal) {
        if (inRecess(d, rule)) return new Skipped(d, SkipReason.RECESS, null);
        String h = cal.publicHoliday(d);
        if (h != null) return new Skipped(d, SkipReason.PUBLIC_HOLIDAY, h);
        String c = cal.courtClosure(d);
        if (c != null) return new Skipped(d, SkipReason.COURT_CLOSURE, c);
        DayOfWeek w = d.getDayOfWeek();
        if (w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY) return new Skipped(d, SkipReason.WEEKEND, null);
        return null;
    }

    static boolean inRecess(LocalDate d, Rule rule) {
        MonthDay s = rule.recessStart(), e = rule.recessEnd();
        if (s == null || e == null) return false;
        MonthDay x = MonthDay.from(d);
        return s.compareTo(e) <= 0 ? !x.isBefore(s) && !x.isAfter(e) : !x.isBefore(s) || !x.isAfter(e);
    }

    private static void guard(int steps) {
        if (steps > MAX_STEPS) throw new IllegalStateException("No open day found: check the court calendar for closures that cover every day");
    }
}
