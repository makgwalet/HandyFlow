package za.co.handyflow.platform.earthmoving.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * How long a machine was out of service in a period, worked out from its recorded status changes. Time in BREAKDOWN and time in
 * MAINTENANCE are kept apart. A period that has not finished yet only counts up to now. A machine with no recorded changes
 * before the period is taken to have been in service at its start.
 */
public final class DowntimeRules {
    private DowntimeRules() {}

    public record Change(Instant at, String toStatus) {}
    public record Result(long breakdownSeconds, long maintenanceSeconds, int breakdowns) {
        public long totalSeconds() { return breakdownSeconds + maintenanceSeconds; }
    }

    /** {@code changes} must belong to one machine and be oldest first. */
    public static Result compute(List<Change> changes, Instant from, Instant to, Instant now) {
        Instant end = to.isAfter(now) ? now : to;
        String current = null;
        for (Change c : changes) if (c.at().isBefore(from)) current = c.toStatus();
        Instant cursor = from;
        long breakdown = 0, maintenance = 0;
        int count = 0;
        for (Change c : changes) {
            if (c.at().isBefore(from) || !c.at().isBefore(end)) continue;
            long s = Duration.between(cursor, c.at()).getSeconds();
            if ("BREAKDOWN".equals(current)) breakdown += s; else if ("MAINTENANCE".equals(current)) maintenance += s;
            current = c.toStatus();
            cursor = c.at();
            if ("BREAKDOWN".equals(current)) count++;
        }
        if (end.isAfter(cursor)) {
            long s = Duration.between(cursor, end).getSeconds();
            if ("BREAKDOWN".equals(current)) breakdown += s; else if ("MAINTENANCE".equals(current)) maintenance += s;
        }
        return new Result(breakdown, maintenance, count);
    }

    /** Percentage of the period (up to now) the machine was in service, to one decimal; null when the period has not started. */
    public static java.math.BigDecimal availabilityPercent(long downSeconds, Instant from, Instant to, Instant now) {
        Instant end = to.isAfter(now) ? now : to;
        long span = Duration.between(from, end).getSeconds();
        if (span <= 0) return null;
        long up = Math.max(0, span - downSeconds);
        return java.math.BigDecimal.valueOf(up).multiply(java.math.BigDecimal.valueOf(100))
                .divide(java.math.BigDecimal.valueOf(span), 1, java.math.RoundingMode.HALF_UP);
    }
}
