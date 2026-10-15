package za.co.handyflow.platform.earthmoving.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Hours worked per day across the fleet, from completed operator shifts. A shift counts on the day it started.
 * Only machines on an hour meter count: a kilometre meter's "hours logged" is distance, and adding it in would be wrong.
 */
public final class UtilisationRules {
    private UtilisationRules() {}

    public static final int MIN_DAYS = 7;
    public static final int MAX_DAYS = 90;

    public record Shift(Instant startedAt, BigDecimal hours) {}

    public static int checkDays(int days) {
        if (days < MIN_DAYS || days > MAX_DAYS)
            throw new IllegalArgumentException("Choose between " + MIN_DAYS + " and " + MAX_DAYS + " days.");
        return days;
    }

    /** One entry for every day from {@code days - 1} days before {@code today} up to {@code today}, oldest first, zero when nothing was worked. */
    public static List<BigDecimal> perDay(List<Shift> shifts, LocalDate today, int days, ZoneId zone) {
        BigDecimal[] sums = new BigDecimal[days];
        for (int i = 0; i < days; i++) sums[i] = BigDecimal.ZERO;
        LocalDate first = today.minusDays(days - 1L);
        for (Shift s : shifts) {
            if (s == null || s.startedAt() == null || s.hours() == null) continue;
            long idx = java.time.temporal.ChronoUnit.DAYS.between(first, s.startedAt().atZone(zone).toLocalDate());
            if (idx < 0 || idx >= days) continue;
            sums[(int) idx] = sums[(int) idx].add(s.hours());
        }
        List<BigDecimal> out = new ArrayList<>(days);
        for (BigDecimal b : sums) out.add(b.setScale(1, java.math.RoundingMode.HALF_UP));
        return out;
    }

    /** How many different days had at least one shift start, out of the days in the period. */
    public static int daysWorked(List<Instant> starts, ZoneId zone) {
        return (int) starts.stream().filter(java.util.Objects::nonNull).map(t -> t.atZone(zone).toLocalDate()).distinct().count();
    }

    /** Share of the days in the period that were worked, to one decimal. */
    public static BigDecimal percentOfDays(int daysWorked, long daysInPeriod) {
        if (daysInPeriod <= 0) return BigDecimal.ZERO.setScale(1);
        return BigDecimal.valueOf(daysWorked * 100L).divide(BigDecimal.valueOf(daysInPeriod), 1, java.math.RoundingMode.HALF_UP);
    }
}
