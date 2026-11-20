package za.co.handyflow.platform.legalcompliance.application.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Pure, explainable trend maths for an indicator's snapshot history. An ordinary least-squares straight line through (date, percent) points, nothing more.
 *
 * It is a description of where the recorded figures have been heading, not a prediction model: it knows nothing about planned work, seasonality or one-off events.
 * So it refuses to draw a line from thin data (fewer than {@link #MIN_POINTS} points or a span under {@link #MIN_SPAN_DAYS} days), shows its fit (r²), and only
 * projects when the fit is reasonable ({@link #MIN_R2}); otherwise it says the direction is unclear. Projections are clamped to 0..100.
 */
public final class ForecastRules {

    private ForecastRules() {}

    public static final int MIN_POINTS = 4;
    public static final long MIN_SPAN_DAYS = 60;
    public static final double MIN_R2 = 0.5;
    /** A slope smaller than this many percentage points per 30 days is reported as steady. */
    public static final double STEADY_BAND = 1.0;

    public record Point(LocalDate date, int percent) {}

    public enum Direction { IMPROVING, DECLINING, STEADY, UNCLEAR, INSUFFICIENT_DATA }

    public record Fit(Direction direction, int points, double slopePer30Days, double r2, Integer projected30, Integer projected90, String note) {}

    /** Points must be in any order; only the dates and percentages are used. Higher is better for every indicator. */
    public static Fit fit(List<Point> input) {
        List<Point> pts = input.stream().sorted(java.util.Comparator.comparing(Point::date)).toList();
        int n = pts.size();
        if (n < MIN_POINTS)
            return new Fit(Direction.INSUFFICIENT_DATA, n, 0, 0, null, null, "Needs at least " + MIN_POINTS + " snapshots with this indicator; there " + (n == 1 ? "is 1" : "are " + n) + ".");
        LocalDate first = pts.get(0).date(), last = pts.get(n - 1).date();
        long span = ChronoUnit.DAYS.between(first, last);
        if (span < MIN_SPAN_DAYS)
            return new Fit(Direction.INSUFFICIENT_DATA, n, 0, 0, null, null, "Needs snapshots spanning at least " + MIN_SPAN_DAYS + " days; these span " + span + ".");

        double mx = 0, my = 0;
        for (Point p : pts) { mx += ChronoUnit.DAYS.between(first, p.date()); my += p.percent(); }
        mx /= n; my /= n;
        double sxx = 0, sxy = 0, syy = 0;
        for (Point p : pts) {
            double dx = ChronoUnit.DAYS.between(first, p.date()) - mx, dy = p.percent() - my;
            sxx += dx * dx; sxy += dx * dy; syy += dy * dy;
        }
        double slope = sxx == 0 ? 0 : sxy / sxx;                       // percentage points per day
        double intercept = my - slope * mx;
        double r2 = syy == 0 ? 1.0 : (sxy * sxy) / (sxx * syy);        // a perfectly flat series is a perfect (flat) fit
        double per30 = round1(slope * 30);

        if (Math.abs(per30) < STEADY_BAND)
            return new Fit(Direction.STEADY, n, per30, round2(r2), project(slope, intercept, first, last, 30), project(slope, intercept, first, last, 90),
                    "Moving by less than " + (int) STEADY_BAND + " point per month.");
        if (r2 < MIN_R2)
            return new Fit(Direction.UNCLEAR, n, per30, round2(r2), null, null, "The figures move too erratically for a straight line to describe them (fit " + round2(r2) + "), so no projection is shown.");
        return new Fit(per30 > 0 ? Direction.IMPROVING : Direction.DECLINING, n, per30, round2(r2), project(slope, intercept, first, last, 30), project(slope, intercept, first, last, 90),
                "Straight-line trend over " + span + " days (fit " + round2(r2) + ").");
    }

    private static int project(double slope, double intercept, LocalDate first, LocalDate last, int daysAhead) {
        double x = ChronoUnit.DAYS.between(first, last) + daysAhead;
        return (int) Math.max(0, Math.min(100, Math.round(intercept + slope * x)));
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
