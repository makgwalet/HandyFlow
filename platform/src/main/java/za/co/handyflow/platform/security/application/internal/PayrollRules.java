package za.co.handyflow.platform.security.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Pure payroll rules, so they can be tested without Spring or a database.
 *
 * Dates in a pay period are dates in the company's zone (South Africa unless the company has set another): a shift that starts at 01:00 on the 1st belongs to the period that contains the 1st, not to the month
 * before (security audit 1.6; the period window used to be cut at UTC midnight, two hours early).
 */
public final class PayrollRules {

    public static final ZoneId ZONE = za.co.handyflow.platform.shared.BusinessTime.DEFAULT_ZONE;
    public static final int DEFAULT_STANDARD_HOURS = 9;

    private PayrollRules() {}

    /** First instant of the period's first day, in South African time. */
    public static Instant windowStart(LocalDate periodStart) { return windowStart(periodStart, ZONE); }

    /** First instant of the period's first day in the company's zone. */
    public static Instant windowStart(LocalDate periodStart, ZoneId zone) {
        return periodStart.atStartOfDay(zoneOr(zone)).toInstant();
    }

    /** First instant AFTER the period's last day, in South African time (exclusive end). */
    public static Instant windowEnd(LocalDate periodEnd) { return windowEnd(periodEnd, ZONE); }

    /** First instant AFTER the period's last day in the company's zone (exclusive end). */
    public static Instant windowEnd(LocalDate periodEnd, ZoneId zone) {
        return periodEnd.plusDays(1).atStartOfDay(zoneOr(zone)).toInstant();
    }

    /** The South African calendar date a shift started on. */
    public static LocalDate shiftDate(Instant startAt) { return shiftDate(startAt, ZONE); }

    /** The calendar date a shift started on in the company's zone. */
    public static LocalDate shiftDate(Instant startAt, ZoneId zone) {
        return startAt.atZone(zoneOr(zone)).toLocalDate();
    }

    /** A missing zone means South Africa, never the server's own zone. */
    private static ZoneId zoneOr(ZoneId zone) { return zone == null ? ZONE : zone; }

    public record Split(BigDecimal regular, BigDecimal overtime) {}

    /** Hours to two decimals from whole minutes (no floating point), split into regular and overtime at the standard hours per day. */
    public static Split split(Instant startAt, Instant endAt, int standardHours) {
        long minutes = Math.max(0, Duration.between(startAt, endAt).toMinutes());
        BigDecimal total = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        BigDecimal regular = total.min(BigDecimal.valueOf(standardHours)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal overtime = total.subtract(regular).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new Split(regular, overtime);
    }
}
