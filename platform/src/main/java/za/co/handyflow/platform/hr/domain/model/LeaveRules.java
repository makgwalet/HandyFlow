package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Leave rules as pure functions. Based on the Basic Conditions of Employment Act for a five-day week:
 * annual leave accrues at one day for every 17 days worked (15 working days a year at most); sick leave is 30 days in a
 * 36-month cycle, and in the first six months one day for every 26 days worked; family responsibility leave is 3 days a
 * year after four months of service. Maternity, paternity and unpaid leave have no balance to check; study leave is
 * granted by HR.
 */
public final class LeaveRules {

    private LeaveRules() {}

    public static final String ANNUAL = "ANNUAL", SICK = "SICK", FAMILY = "FAMILY_RESPONSIBILITY", MATERNITY = "MATERNITY",
            PATERNITY = "PATERNITY", UNPAID = "UNPAID", STUDY = "STUDY";
    public static final List<String> TYPES = List.of(ANNUAL, SICK, FAMILY, MATERNITY, PATERNITY, STUDY, UNPAID);
    /** Types whose requests are checked against a balance. */
    public static final Set<String> CAPPED = Set.of(ANNUAL, SICK, FAMILY, STUDY);

    public static final String OPENING = "OPENING", ACCRUAL = "ACCRUAL", CARRY_FORWARD = "CARRY_FORWARD", ADJUSTMENT = "ADJUSTMENT",
            RESERVED = "RESERVED", RELEASED = "RELEASED", TAKEN = "TAKEN", REVERSAL = "REVERSAL";

    public static final BigDecimal ANNUAL_PER_YEAR = new BigDecimal("15");
    public static final BigDecimal SICK_PER_CYCLE = new BigDecimal("30");
    public static final BigDecimal FAMILY_PER_YEAR = new BigDecimal("3");
    private static final BigDecimal HALF = new BigDecimal("0.5");

    public static boolean isKnown(String type) { return type != null && TYPES.contains(type); }
    public static boolean isCapped(String type) { return CAPPED.contains(type); }

    // ── Days ────────────────────────────────────────────────────────────────────────────────────

    /** Monday to Friday, public holidays left out. */
    public static List<LocalDate> workingDays(LocalDate from, LocalDate to, Set<LocalDate> holidays) {
        List<LocalDate> out = new ArrayList<>();
        if (from == null || to == null || to.isBefore(from)) return out;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) if (isWeekday(d) && !holidays.contains(d)) out.add(d);
        return out;
    }

    /** Monday to Friday between two dates inclusive, holidays included: days that count as worked for accrual. */
    public static int weekdays(LocalDate from, LocalDate to) {
        int n = 0;
        if (from == null || to == null) return 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) if (isWeekday(d)) n++;
        return n;
    }

    private static boolean isWeekday(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY;
    }

    /** Days a request uses, by year of each day, so a request over a year end is charged to both years. */
    public static SortedMap<Integer, BigDecimal> daysByYear(List<LocalDate> days, boolean halfDay) {
        SortedMap<Integer, BigDecimal> m = new TreeMap<>();
        for (LocalDate d : days) m.merge(d.getYear(), halfDay ? HALF : BigDecimal.ONE, BigDecimal::add);
        return m;
    }

    public static BigDecimal totalDays(List<LocalDate> days, boolean halfDay) {
        return halfDay ? (days.isEmpty() ? BigDecimal.ZERO : HALF) : BigDecimal.valueOf(days.size());
    }

    // ── Entitlement ─────────────────────────────────────────────────────────────────────────────

    /**
     * Annual leave earned in a calendar year by a date: one day for every 17 weekdays from the later of the start of
     * employment, the start of the ledger and 1 January, up to 15. Rounded down to two decimals.
     */
    public static BigDecimal annualEarned(LocalDate employmentStart, LocalDate ledgerStart, int year, LocalDate asOf) {
        LocalDate from = latest(employmentStart, ledgerStart, LocalDate.of(year, 1, 1));
        LocalDate to = asOf.isAfter(LocalDate.of(year, 12, 31)) ? LocalDate.of(year, 12, 31) : asOf;
        if (to.isBefore(from)) return BigDecimal.ZERO;
        BigDecimal earned = BigDecimal.valueOf(weekdays(from, to)).divide(BigDecimal.valueOf(17), 2, RoundingMode.DOWN);
        return earned.min(ANNUAL_PER_YEAR);
    }

    /** Family responsibility leave: 3 days a year once four months of service are complete. */
    public static BigDecimal familyEarned(LocalDate employmentStart, int year, LocalDate asOf) {
        LocalDate eligible = employmentStart.plusMonths(4);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        if (asOf.isBefore(eligible) || eligible.isAfter(yearEnd) || asOf.getYear() < year) return BigDecimal.ZERO;
        return FAMILY_PER_YEAR;
    }

    public record Cycle(LocalDate from, LocalDate to) {
        public boolean contains(LocalDate d) { return !d.isBefore(from) && !d.isAfter(to); }
    }

    /** The 36-month sick leave cycle that contains the date, counted from the start of employment. */
    public static Cycle sickCycle(LocalDate employmentStart, LocalDate date) {
        if (date.isBefore(employmentStart)) return new Cycle(employmentStart, employmentStart.plusMonths(36).minusDays(1));
        int k = 0;
        while (!date.isBefore(employmentStart.plusMonths(36L * (k + 1)))) k++;
        return new Cycle(employmentStart.plusMonths(36L * k), employmentStart.plusMonths(36L * (k + 1)).minusDays(1));
    }

    /**
     * Sick leave earned in the cycle by a date. In the first six months of employment one day for every 26 days worked;
     * from then on the full 30 days for the cycle (days taken earlier in the cycle count against it). Later cycles start
     * with 30.
     */
    public static BigDecimal sickEarned(LocalDate employmentStart, LocalDate asOf) {
        Cycle c = sickCycle(employmentStart, asOf);
        boolean firstCycle = c.from().equals(employmentStart);
        if (firstCycle && asOf.isBefore(employmentStart.plusMonths(6))) {
            return BigDecimal.valueOf(weekdays(employmentStart, asOf) / 26);
        }
        return SICK_PER_CYCLE;
    }

    // ── Requests ────────────────────────────────────────────────────────────────────────────────

    /** @return why the request cannot be made, or null. Balance is checked separately. */
    public static String requestProblem(String type, LocalDate start, LocalDate end, boolean halfDay, int workingDayCount,
                                        LocalDate employmentStart, String employeeStatus, LocalDate today) {
        if (!isKnown(type)) return "Choose a leave type.";
        if (start == null || end == null) return "Enter the first and last day of the leave.";
        if (end.isBefore(start)) return "The last day is before the first day.";
        if (halfDay && !start.equals(end)) return "A half day can only be one day.";
        if (!"ACTIVE".equals(employeeStatus)) return "Leave can only be requested for an employee who is currently active.";
        if (start.isBefore(employmentStart)) return "The leave starts before the employee's start date.";
        if (end.isAfter(start.plusDays(365))) return "A leave request can cover at most a year.";
        if (start.isAfter(today.plusDays(400))) return "That is too far ahead to request.";
        if (workingDayCount == 0) return "There is no working day in that period (weekends and public holidays are not leave days).";
        if (ANNUAL.equals(type) || STUDY.equals(type)) {
            if (start.getYear() < today.getYear()) return "Leave cannot start in a year that has closed. Ask HR to record an adjustment.";
        } else if (start.isBefore(today.minusDays(90))) {
            return "Leave cannot start more than 90 days ago. Ask HR to record an adjustment.";
        }
        return null;
    }

    /** More than two consecutive working days of sick leave needs a medical certificate (BCEA section 23). */
    public static boolean certificateExpected(String type, int workingDayCount) {
        return SICK.equals(type) && workingDayCount > 2;
    }

    public static boolean overlaps(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        return !aEnd.isBefore(bStart) && !bEnd.isBefore(aStart);
    }

    private static LocalDate latest(LocalDate... ds) {
        LocalDate r = ds[0];
        for (LocalDate d : ds) if (d.isAfter(r)) r = d;
        return r;
    }
}
