package za.co.handyflow.platform.hr.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Saved HR reports: which settings are allowed, which dates a relative period means today, and which day a reminder is due.
 * Pure. A saved report holds settings only; it never holds report figures, so saving one cannot widen who sees what.
 */
public final class SavedReportRules {

    private SavedReportRules() {}

    public static final String WORKFORCE = "WORKFORCE", TIME = "TIME", PAYROLL = "PAYROLL", COMPLIANCE = "COMPLIANCE";
    public static final Set<String> KINDS = Set.of(WORKFORCE, TIME, PAYROLL, COMPLIANCE);

    public static final String THIS_MONTH = "THIS_MONTH", LAST_MONTH = "LAST_MONTH", THIS_QUARTER = "THIS_QUARTER", LAST_QUARTER = "LAST_QUARTER",
            YEAR_TO_DATE = "YEAR_TO_DATE", LAST_90_DAYS = "LAST_90_DAYS", LAST_12_MONTHS = "LAST_12_MONTHS";
    public static final Set<String> PERIODS = Set.of(THIS_MONTH, LAST_MONTH, THIS_QUARTER, LAST_QUARTER, YEAR_TO_DATE, LAST_90_DAYS, LAST_12_MONTHS);

    public static final String NONE = "NONE", WEEKLY = "WEEKLY", MONTHLY = "MONTHLY";
    public static final Set<String> SCHEDULES = Set.of(NONE, WEEKLY, MONTHLY);

    public static final int MAX_NAME = 80;
    /** Enough for anyone's real use, and it stops a loop in a script filling the table. */
    public static final int MAX_PER_PERSON = 20;
    /** How many colleagues one report can be shared with. */
    public static final int MAX_SHARES = 20;

    /** Null when the settings are acceptable, otherwise a sentence to show the person. */
    public static String problem(String name, String kind, String period, String schedule, Integer runDay) {
        if (name == null || name.isBlank()) return "Give the saved report a name.";
        if (name.trim().length() > MAX_NAME) return "Keep the name to " + MAX_NAME + " characters or fewer.";
        if (kind == null || !KINDS.contains(kind)) return "Choose which report to save.";
        if (period == null || !PERIODS.contains(period)) return "Choose the period the report should cover.";
        String sched = schedule == null ? NONE : schedule;
        if (!SCHEDULES.contains(sched)) return "Choose no reminder, weekly or monthly.";
        if (NONE.equals(sched)) return runDay == null ? null : "A report with no reminder has no reminder day.";
        if (runDay == null) return "Choose the day of the reminder.";
        if (WEEKLY.equals(sched) && (runDay < 1 || runDay > 7)) return "Choose a weekday from Monday to Sunday.";
        if (MONTHLY.equals(sched) && (runDay < 1 || runDay > 28)) return "Choose a day of the month from 1 to 28, so every month has it.";
        return null;
    }

    /** Only people who may see payroll figures may keep a payroll report; the other reports already limit themselves to what the viewer holds. */
    public static boolean mayKeep(String kind, boolean holdsCompensation) { return !PAYROLL.equals(kind) || holdsCompensation; }

    /** First and last day (inclusive) the period means on {@code today}. */
    public static LocalDate[] resolve(String period, LocalDate today) {
        int q = (today.getMonthValue() - 1) / 3 * 3 + 1;
        LocalDate thisQuarter = LocalDate.of(today.getYear(), q, 1);
        return switch (period) {
            case THIS_MONTH -> new LocalDate[]{today.withDayOfMonth(1), today};
            case LAST_MONTH -> {
                LocalDate first = today.withDayOfMonth(1).minusMonths(1);
                yield new LocalDate[]{first, first.withDayOfMonth(first.lengthOfMonth())};
            }
            case THIS_QUARTER -> new LocalDate[]{thisQuarter, today};
            case LAST_QUARTER -> new LocalDate[]{thisQuarter.minusMonths(3), thisQuarter.minusDays(1)};
            case YEAR_TO_DATE -> new LocalDate[]{LocalDate.of(today.getYear(), 1, 1), today};
            case LAST_90_DAYS -> new LocalDate[]{today.minusDays(89), today};
            case LAST_12_MONTHS -> new LocalDate[]{today.minusDays(364), today};
            default -> throw new IllegalArgumentException("Unknown period " + period);
        };
    }

    /** Whether today is a reminder day that has not already been used. A weekly or monthly report is reminded at most once a day. */
    public static boolean dueToday(String schedule, Integer runDay, LocalDate lastNotifiedOn, LocalDate today) {
        if (schedule == null || runDay == null || NONE.equals(schedule)) return false;
        if (lastNotifiedOn != null && !lastNotifiedOn.isBefore(today)) return false;
        return switch (schedule) {
            case WEEKLY -> today.getDayOfWeek() == DayOfWeek.of(runDay);
            case MONTHLY -> today.getDayOfMonth() == runDay;
            default -> false;
        };
    }

    public static String kindLabel(String kind) {
        return switch (kind) {
            case WORKFORCE -> "Workforce";
            case TIME -> "Leave and attendance";
            case PAYROLL -> "Payroll";
            case COMPLIANCE -> "Compliance";
            default -> kind;
        };
    }

    public static String periodLabel(String period) {
        return switch (period) {
            case THIS_MONTH -> "this month";
            case LAST_MONTH -> "last month";
            case THIS_QUARTER -> "this quarter";
            case LAST_QUARTER -> "last quarter";
            case YEAR_TO_DATE -> "year to date";
            case LAST_90_DAYS -> "the last 90 days";
            case LAST_12_MONTHS -> "the last 12 months";
            default -> period;
        };
    }

    public static String reminderTitle(String name) { return "Report due: " + name; }

    /** Names the report and its dates and nothing else. The figures are not in the message. */
    public static String reminderMessage(String kind, String period, LocalDate from, LocalDate to) {
        return "Your " + kindLabel(kind).toLowerCase() + " report for " + periodLabel(period) + " (" + from + " to " + to
                + ") is ready to open. Open it to see the figures.";
    }
}
