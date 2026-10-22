package za.co.handyflow.platform.hr.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pay for the days employed and paid in a pay period. No database, no clock.
 * <p>
 * A day counts when it is a Monday to Friday and not a public holiday. The monthly salary is spread over the working days of the
 * period: someone paid for 12 of 21 working days gets 12/21 of the month. Six-day weeks, shift patterns and individual schedules are not
 * considered. The annual-leave daily rate is the monthly salary times 12 divided by 260 working days a year (about 21.67 a month).
 */
public final class PayPeriodRules {

    private PayPeriodRules() {}

    public static final int WORKING_DAYS_PER_YEAR = 260;
    /** A leaver recorded late is still paid in a later run, but not one who left more than this many months before the period. */
    public static final int LATE_LEAVER_MONTHS = 3;

    public static List<LocalDate> workingDays(LocalDate from, LocalDate to, Set<LocalDate> holidays) {
        List<LocalDate> out = new ArrayList<>();
        if (from == null || to == null) return out;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            int dow = d.getDayOfWeek().getValue();
            if (dow <= 5 && !holidays.contains(d)) out.add(d);
        }
        return out;
    }

    /**
     * What an employee is paid for in a period.
     * @param workingDays   working days in the whole period
     * @param daysEmployed  of those, the days between the start and end of employment
     * @param unpaidDays    of the days employed, those on approved unpaid leave
     * @param daysPaid      days employed less unpaid days
     * @param salary        the full monthly salary
     * @param proration     salary not paid because of joining or leaving part way through
     * @param unpaidAmount  salary not paid for unpaid leave
     * @param paid          salary payable: salary less proration less unpaid amount
     */
    public record Pay(int workingDays, BigDecimal daysEmployed, BigDecimal unpaidDays, BigDecimal daysPaid,
                      BigDecimal salary, BigDecimal proration, BigDecimal unpaidAmount, BigDecimal paid) {
        public boolean partPeriod() { return proration.signum() != 0 || unpaidAmount.signum() != 0; }
    }

    /**
     * @param unpaidDays working days of approved unpaid leave inside the period (half days count as 0.5); only those inside the employment
     *                   are counted
     */
    public static Pay pay(BigDecimal salary, LocalDate periodStart, LocalDate periodEnd, Set<LocalDate> holidays,
                          LocalDate employedFrom, LocalDate employedTo, BigDecimal unpaidDays) {
        List<LocalDate> days = workingDays(periodStart, periodEnd, holidays);
        int total = days.size();
        long employed = days.stream().filter(d -> (employedFrom == null || !d.isBefore(employedFrom)) && (employedTo == null || !d.isAfter(employedTo))).count();
        BigDecimal emp = BigDecimal.valueOf(employed);
        BigDecimal unpaid = unpaidDays == null ? BigDecimal.ZERO : unpaidDays.max(BigDecimal.ZERO).min(emp);
        BigDecimal paidDays = emp.subtract(unpaid);
        if (total == 0) {                       // a period of nothing but weekends and holidays: nothing to spread the salary over
            return new Pay(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, salary, BigDecimal.ZERO, BigDecimal.ZERO, salary);
        }
        BigDecimal perDay = salary.divide(BigDecimal.valueOf(total), 10, RoundingMode.HALF_UP);
        BigDecimal proration = employed == total ? BigDecimal.ZERO : money(perDay.multiply(BigDecimal.valueOf(total - employed)));
        BigDecimal unpaidAmount = unpaid.signum() == 0 ? BigDecimal.ZERO : money(perDay.multiply(unpaid));
        BigDecimal paid = salary.subtract(proration).subtract(unpaidAmount).max(BigDecimal.ZERO);
        return new Pay(total, emp, unpaid, paidDays, salary, proration, unpaidAmount, paid);
    }

    /** The same share of any other amount paid by the month, such as a travel allowance. */
    public static BigDecimal share(BigDecimal monthly, Pay pay) {
        if (monthly == null || pay.workingDays() == 0) return monthly == null ? BigDecimal.ZERO : monthly;
        if (pay.daysPaid().compareTo(BigDecimal.valueOf(pay.workingDays())) == 0) return monthly;
        return money(monthly.multiply(pay.daysPaid()).divide(BigDecimal.valueOf(pay.workingDays()), 10, RoundingMode.HALF_UP));
    }

    /** Working days of an approved unpaid leave inside the period. A half day is 0.5 and is only ever one day. */
    public static BigDecimal unpaidDaysIn(LocalDate leaveStart, LocalDate leaveEnd, boolean halfDay, LocalDate periodStart, LocalDate periodEnd,
                                          Set<LocalDate> holidays) {
        LocalDate from = leaveStart.isAfter(periodStart) ? leaveStart : periodStart;
        LocalDate to = leaveEnd.isBefore(periodEnd) ? leaveEnd : periodEnd;
        if (to.isBefore(from)) return BigDecimal.ZERO;
        int n = workingDays(from, to, holidays).size();
        if (halfDay) return n > 0 ? new BigDecimal("0.5") : BigDecimal.ZERO;
        return BigDecimal.valueOf(n);
    }

    // ── Last pay ───────────────────────────────────────────────────────────────────────────────

    /** Whether this pay is the employee's last: terminated, with a leaving date on or before the end of the period. */
    public static boolean isFinal(String status, LocalDate endDate, LocalDate periodEnd) {
        return "TERMINATED".equals(status) && endDate != null && !endDate.isAfter(periodEnd);
    }

    /** The oldest leaving date a late-recorded leaver can have and still be paid in a run for this period. */
    public static LocalDate earliestLateLeaver(LocalDate periodStart) { return periodStart.minusMonths(LATE_LEAVER_MONTHS); }

    public static BigDecimal dailyRate(BigDecimal monthlySalary) {
        return monthlySalary.multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(WORKING_DAYS_PER_YEAR), 2, RoundingMode.HALF_UP);
    }

    /** Annual leave owing, paid at the daily rate. Days are paid to two decimals; nothing is paid for a negative balance. */
    public static BigDecimal leavePayout(BigDecimal monthlySalary, BigDecimal days) {
        if (days == null || days.signum() <= 0) return BigDecimal.ZERO;
        return money(dailyRate(monthlySalary).multiply(days));
    }

    private static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
}
