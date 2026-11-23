package za.co.handyflow.platform.security.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Sunday and public-holiday pay on top of ordinary and overtime pay. Pure: no database, no Spring.
 *
 * A shift is cut at midnight in the company's zone. Hours on a public holiday are paid at the holiday multiplier, hours on a Sunday at the
 * Sunday multiplier, and a day that is both pays the holiday rate. Overtime is still the hours beyond the standard day, counted from the start of
 * the shift; an hour of overtime on a premium day is paid at the premium rate, which is never below the overtime rate (1.5).
 * Everything else is paid exactly as before, so a shift with no premium hour gives no premium lines at all.
 */
public final class PayPremiumRules {
    private PayPremiumRules() {}

    public static final BigDecimal MIN_MULTIPLIER = new BigDecimal("1.50");
    public static final BigDecimal MAX_MULTIPLIER = new BigDecimal("4.00");
    public static final BigDecimal DEFAULT_SUNDAY = new BigDecimal("1.50");
    public static final BigDecimal DEFAULT_HOLIDAY = new BigDecimal("2.00");

    public record Settings(boolean enabled, BigDecimal sundayMultiplier, BigDecimal holidayMultiplier) {
        public static final Settings OFF = new Settings(false, DEFAULT_SUNDAY, DEFAULT_HOLIDAY);
    }

    public enum Kind { NORMAL, SUNDAY, HOLIDAY }

    /** What a company has changed about the public-holiday list: `added` are extra days, `removed` are days it does not observe. */
    public record Overrides(Map<LocalDate, String> added, java.util.Set<LocalDate> removed) {
        public static final Overrides NONE = new Overrides(Map.of(), java.util.Set.of());
    }

    /** Checks the settings a person typed. Both multipliers are checked even when the premium is off, so the saved values are always sensible. */
    public static Settings validate(boolean enabled, BigDecimal sunday, BigDecimal holiday) {
        BigDecimal s = check("Sunday", sunday), h = check("Public holiday", holiday);
        return new Settings(enabled, s, h);
    }

    private static BigDecimal check(String what, BigDecimal v) {
        if (v == null) throw new HandyFlowException(what + " multiplier is required.", HttpStatus.BAD_REQUEST, "PREMIUM_MULTIPLIER");
        BigDecimal x = v.stripTrailingZeros();
        if (x.scale() > 2) throw new HandyFlowException(what + " multiplier can have at most two decimals.", HttpStatus.BAD_REQUEST, "PREMIUM_MULTIPLIER");
        if (v.compareTo(MIN_MULTIPLIER) < 0 || v.compareTo(MAX_MULTIPLIER) > 0) {
            throw new HandyFlowException(what + " multiplier must be between 1.50 and 4.00. It cannot be below the overtime rate.", HttpStatus.BAD_REQUEST, "PREMIUM_MULTIPLIER");
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Is this date a public holiday for the company: the national list, plus the days it added, minus the days it removed. */
    public static boolean isHoliday(LocalDate date, Overrides o) {
        if (o.removed().contains(date)) return false;
        if (o.added().containsKey(date)) return true;
        return SaPublicHolidays.nameOn(date) != null;
    }

    public static Kind kindOf(LocalDate date, Predicate<LocalDate> isHoliday) {
        if (isHoliday.test(date)) return Kind.HOLIDAY;
        return date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY ? Kind.SUNDAY : Kind.NORMAL;
    }

    /** The minutes of a shift in each bucket. Premium minutes include the shift's overtime on that day. */
    public record Buckets(long normalRegularMinutes, long normalOvertimeMinutes, long sundayMinutes, long holidayMinutes) {
        public long total() { return normalRegularMinutes + normalOvertimeMinutes + sundayMinutes + holidayMinutes; }
        public boolean hasPremium() { return sundayMinutes > 0 || holidayMinutes > 0; }
    }

    /** Cuts the shift at midnight in `zone`. The minutes add up to the shift's whole minutes, the same count the ordinary split uses. */
    public static Buckets apportion(Instant start, Instant end, ZoneId zone, int standardHours, Predicate<LocalDate> isHoliday) {
        long total = Math.max(0, Duration.between(start, end).toMinutes());
        long standard = Math.min(total, Math.max(0, standardHours) * 60L);
        long nReg = 0, nOt = 0, sun = 0, hol = 0;
        Instant cursor = start;
        while (cursor.isBefore(end)) {
            LocalDate day = cursor.atZone(zone).toLocalDate();
            Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
            Instant segEnd = dayEnd.isBefore(end) ? dayEnd : end;
            long from = minutesSince(start, cursor), to = Math.min(total, minutesSince(start, segEnd));
            long len = Math.max(0, to - from);
            if (len > 0) {
                switch (kindOf(day, isHoliday)) {
                    case HOLIDAY -> hol += len;
                    case SUNDAY -> sun += len;
                    case NORMAL -> {
                        long reg = Math.max(0, Math.min(to, standard) - from);
                        nReg += reg;
                        nOt += len - reg;
                    }
                }
            }
            cursor = segEnd;
        }
        return new Buckets(nReg, nOt, sun, hol);
    }

    private static long minutesSince(Instant start, Instant t) { return Duration.between(start, t).getSeconds() / 60; }

    public static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /** The rate paid for a premium hour: the ordinary rate times the multiplier, to the cent. */
    public static int premiumRateCents(int baseRateCents, BigDecimal multiplier) {
        return BigDecimal.valueOf(baseRateCents).multiply(multiplier).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /** One line to pay. `payRateCents` is the rate the hours are paid at. */
    public record Line(String type, BigDecimal hours, int payRateCents) {}

    /**
     * The lines for one shift, or null when the shift has no premium hour (or the premium is off): the caller then pays it the ordinary way, unchanged.
     * Lines come in a fixed order and a line with no hours is left out.
     */
    public static List<Line> lines(Instant start, Instant end, ZoneId zone, int standardHours, int baseRateCents, Settings settings, Predicate<LocalDate> isHoliday) {
        if (settings == null || !settings.enabled()) return null;
        Buckets b = apportion(start, end, zone, standardHours, isHoliday);
        if (!b.hasPremium()) return null;
        List<Line> out = new ArrayList<>();
        if (b.normalRegularMinutes() > 0) out.add(new Line("REGULAR", hours(b.normalRegularMinutes()), baseRateCents));
        if (b.normalOvertimeMinutes() > 0) out.add(new Line("OVERTIME", hours(b.normalOvertimeMinutes()), (int) (baseRateCents * 1.5)));
        if (b.sundayMinutes() > 0) out.add(new Line("SUNDAY", hours(b.sundayMinutes()), premiumRateCents(baseRateCents, settings.sundayMultiplier())));
        if (b.holidayMinutes() > 0) out.add(new Line("PUBLIC_HOLIDAY", hours(b.holidayMinutes()), premiumRateCents(baseRateCents, settings.holidayMultiplier())));
        return out;
    }

    /** Overtime, Sunday and public-holiday lines are paid at the line's own rate (overtimeRateCents); the others at the ordinary rate. */
    public static boolean paidAtLineRate(String lineType) {
        return "OVERTIME".equals(lineType) || "SUNDAY".equals(lineType) || "PUBLIC_HOLIDAY".equals(lineType);
    }

    /** Only an overtime line keeps its hours in the overtime-hours column; a Sunday or holiday line keeps them in hours worked. */
    public static boolean hoursInOvertimeColumn(String lineType) { return "OVERTIME".equals(lineType); }

    /** What a pay statement calls the line. */
    public static String label(String lineType) {
        return switch (lineType == null ? "" : lineType) {
            case "SUNDAY" -> "SUNDAY";
            case "PUBLIC_HOLIDAY" -> "PUBLIC HOLIDAY";
            default -> lineType == null ? "" : lineType;
        };
    }
}
