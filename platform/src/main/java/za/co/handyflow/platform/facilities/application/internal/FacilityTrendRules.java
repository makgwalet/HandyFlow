package za.co.handyflow.platform.facilities.application.internal;

import za.co.handyflow.platform.facilities.dto.FacilityTrendResponse.Point;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Buckets work into ISO weeks (Monday start) or calendar months. Pure: no persistence, no clock. */
public final class FacilityTrendRules {

    public static final List<String> BUCKETS = List.of("WEEK", "MONTH");

    private FacilityTrendRules() {}

    /** What the trend needs to know about a completed job. */
    public record Done(Instant createdAt, Instant completedAt, int reworkCount, Integer downtimeMinutes, BigDecimal cost) {}

    public static String normaliseBucket(String bucket) {
        String b = bucket == null || bucket.isBlank() ? "WEEK" : bucket.trim().toUpperCase();
        if (!BUCKETS.contains(b)) throw new IllegalArgumentException("bucket must be one of " + BUCKETS);
        return b;
    }

    public static LocalDate bucketStart(LocalDate d, String bucket) {
        return "MONTH".equals(bucket) ? d.withDayOfMonth(1) : d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate nextStart(LocalDate start, String bucket) {
        return "MONTH".equals(bucket) ? start.plusMonths(1) : start.plusDays(7);
    }

    /**
     * @param raisedOn  the date (in the facilities time zone) each work order in the period was created
     * @param completed the jobs completed in the period, with the date they were completed
     */
    public static List<Point> trend(LocalDate from, LocalDate to, String bucket, Collection<LocalDate> raisedOn,
                                    Collection<Done> completed) {
        String b = normaliseBucket(bucket);
        List<LocalDate> starts = new ArrayList<>();
        for (LocalDate s = bucketStart(from, b); !s.isAfter(to); s = nextStart(s, b)) starts.add(s);

        long[] raised = new long[starts.size()];
        long[] done = new long[starts.size()];
        long[] reworked = new long[starts.size()];
        long[] downtime = new long[starts.size()];
        double[] hours = new double[starts.size()];
        int[] timed = new int[starts.size()];
        BigDecimal[] cost = new BigDecimal[starts.size()];
        java.util.Arrays.fill(cost, BigDecimal.ZERO);

        for (LocalDate d : raisedOn) {
            int i = indexOf(starts, d, b);
            if (i >= 0) raised[i]++;
        }
        for (Done j : completed) {
            int i = indexOf(starts, FacilityTime.toLocalDate(j.completedAt()), b);
            if (i < 0) continue;
            done[i]++;
            if (j.reworkCount() > 0) reworked[i]++;
            if (j.downtimeMinutes() != null && j.downtimeMinutes() > 0) downtime[i] += j.downtimeMinutes();
            if (j.cost() != null) cost[i] = cost[i].add(j.cost());
            if (j.createdAt() != null && j.completedAt() != null) {
                hours[i] += Math.max(0, Duration.between(j.createdAt(), j.completedAt()).getSeconds()) / 3600.0;
                timed[i]++;
            }
        }

        List<Point> points = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            BigDecimal avg = timed[i] == 0 ? null
                    : BigDecimal.valueOf(hours[i] / timed[i]).setScale(2, RoundingMode.HALF_UP);
            points.add(new Point(starts.get(i), nextStart(starts.get(i), b).minusDays(1), raised[i], done[i], avg,
                    reworked[i], downtime[i], cost[i]));
        }
        return points;
    }

    private static int indexOf(List<LocalDate> starts, LocalDate d, String bucket) {
        LocalDate s = bucketStart(d, bucket);
        return starts.indexOf(s);
    }
}
