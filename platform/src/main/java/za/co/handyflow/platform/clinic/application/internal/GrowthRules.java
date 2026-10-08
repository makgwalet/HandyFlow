package za.co.handyflow.platform.clinic.application.internal;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Growth chart arithmetic and the review lifecycle of reference sets (CLINIC-DEC-013, 015, 019). Pure. It holds no reference
 * values: the LMS formula is standard maths, the numbers it works on come only from an ACTIVE, approved set.
 */
public final class GrowthRules {
    private GrowthRules() {}

    public record Point(double ageMonths, double l, double m, double s) {}

    public static final Set<String> MEASURES = Set.of("WEIGHT", "HEIGHT", "HEAD_CIRCUMFERENCE", "BMI");
    public static final String BANNER = "DATA NOT CLINICALLY APPROVED";

    /** Allowed moves. APPROVED to ACTIVE must be a different person from the reviewer (checked in activate). */
    private static final Map<String, Set<String>> NEXT = Map.of(
            "DRAFT", Set.of("CLINICAL_REVIEW"),
            "CLINICAL_REVIEW", Set.of("DRAFT", "APPROVED"),
            "APPROVED", Set.of("ACTIVE", "DRAFT"),
            "ACTIVE", Set.of("RETIRED"),
            "RETIRED", Set.of());

    public static String measure(String raw) {
        String m = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!MEASURES.contains(m)) throw new IllegalArgumentException("Measure must be one of " + MEASURES);
        return m;
    }

    public static String sex(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!s.equals("MALE") && !s.equals("FEMALE")) throw new IllegalArgumentException("Sex must be MALE or FEMALE");
        return s;
    }

    /** Age in completed months with a fractional part, from the patient's date of birth. */
    public static double ageMonths(LocalDate dob, LocalDate on) {
        if (dob == null || on == null) throw new IllegalArgumentException("Date of birth is needed to chart growth");
        if (on.isBefore(dob)) throw new IllegalArgumentException("Measurement is before the date of birth");
        long whole = ChronoUnit.MONTHS.between(dob, on);
        LocalDate anchor = dob.plusMonths(whole);
        LocalDate next = dob.plusMonths(whole + 1);
        double frac = (double) ChronoUnit.DAYS.between(anchor, on) / Math.max(1, ChronoUnit.DAYS.between(anchor, next));
        return whole + frac;
    }

    /** Validate points for import. Ascending unique ages, inside the stated range, M and S above zero. */
    public static void requirePoints(List<Point> points, double min, double max) {
        if (points == null || points.size() < 2) throw new IllegalArgumentException("A reference set needs at least 2 points");
        double prev = -1;
        for (Point p : points) {
            if (p.ageMonths() < min || p.ageMonths() > max) throw new IllegalArgumentException("Point at " + p.ageMonths() + " months is outside " + min + " to " + max);
            if (p.ageMonths() <= prev) throw new IllegalArgumentException("Point ages must be ascending and unique");
            if (!(p.m() > 0) || !(p.s() > 0)) throw new IllegalArgumentException("M and S must be above zero at " + p.ageMonths() + " months");
            if (Double.isNaN(p.l()) || Double.isInfinite(p.l())) throw new IllegalArgumentException("L is not a number at " + p.ageMonths() + " months");
            prev = p.ageMonths();
        }
        if (points.get(0).ageMonths() != min || points.get(points.size() - 1).ageMonths() != max)
            throw new IllegalArgumentException("Points must start at the minimum age and end at the maximum age of the set");
    }

    /** Linear interpolation of L, M and S at an age; empty when the age is outside the set (never extrapolates). */
    public static java.util.Optional<Point> at(List<Point> pts, double age) {
        if (pts.isEmpty() || age < pts.get(0).ageMonths() || age > pts.get(pts.size() - 1).ageMonths()) return java.util.Optional.empty();
        for (int i = 0; i < pts.size(); i++) {
            Point b = pts.get(i);
            if (age == b.ageMonths()) return java.util.Optional.of(b);
            if (age < b.ageMonths()) {
                Point a = pts.get(i - 1);
                double t = (age - a.ageMonths()) / (b.ageMonths() - a.ageMonths());
                return java.util.Optional.of(new Point(age, a.l() + t * (b.l() - a.l()), a.m() + t * (b.m() - a.m()), a.s() + t * (b.s() - a.s())));
            }
        }
        return java.util.Optional.empty();
    }

    /** Z-score by the LMS method. */
    public static double zScore(double value, Point p) {
        if (!(value > 0)) throw new IllegalArgumentException("Value must be above zero");
        return Math.abs(p.l()) < 1e-9 ? Math.log(value / p.m()) / p.s() : (Math.pow(value / p.m(), p.l()) - 1) / (p.l() * p.s());
    }

    /** The measurement that sits at a given z-score (inverse of zScore); used to draw the reference curves. */
    public static double valueAtZ(double z, Point p) {
        return Math.abs(p.l()) < 1e-9 ? p.m() * Math.exp(p.s() * z) : p.m() * Math.pow(1 + p.l() * p.s() * z, 1.0 / p.l());
    }

    /** Percentile (0 to 100) of a z-score on the standard normal curve. */
    public static double percentile(double z) { return 100.0 * normalCdf(z); }

    // Abramowitz and Stegun 7.1.26 for erf, error below 1.5e-7
    private static double normalCdf(double z) {
        double x = Math.abs(z) / Math.sqrt(2);
        double t = 1 / (1 + 0.3275911 * x);
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return z >= 0 ? 0.5 * (1 + y) : 0.5 * (1 - y);
    }

    public static void requireMove(String from, String to) {
        if (!NEXT.getOrDefault(from, Set.of()).contains(to))
            throw new IllegalStateException("A " + from + " reference set cannot move to " + to);
    }

    /** ACTIVE needs a different person from the reviewer. */
    public static void requireDifferentActivator(java.util.UUID reviewer, java.util.UUID activator) {
        if (reviewer == null) throw new IllegalStateException("The set has not been reviewed");
        if (reviewer.equals(activator)) throw new IllegalStateException("The person who reviewed the set cannot approve and activate it");
    }

    /** What the chart may say about a measure. */
    public static String banner(boolean hasActiveSet) { return hasActiveSet ? null : BANNER; }
}
