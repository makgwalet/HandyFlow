// security/application/internal/WorkingTimePolicy.java
package za.co.handyflow.platform.security.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.security.application.internal.WeeklyHoursRules.Thresholds;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Working-time limits for one employee category, effective from a date. Pure rules: validation, and which policy applies to a week.
 * The general BCEA figures are the starting point (45 ordinary, 10 overtime, 12 daily, 3 daily overtime, 12 h rest); a policy that goes
 * beyond them is accepted only with a written legal basis (sectoral determination, bargaining council or collective agreement).
 * The combined weekly ceiling is derived (ordinary + overtime), never entered separately.
 */
public final class WorkingTimePolicy {

    public static final String DEFAULT_CATEGORY = "DEFAULT";
    static final Pattern CATEGORY = Pattern.compile("^[A-Z0-9_]{1,40}$");
    static final double BCEA_ORDINARY_WEEKLY = 45, BCEA_OVERTIME_WEEKLY = 10, BCEA_DAILY = 12, BCEA_DAILY_OVERTIME = 3, BCEA_MIN_REST = 12;
    static final int BASIS_MIN = 10, BASIS_MAX = 500;

    public record Policy(UUID id, String category, LocalDate effectiveFrom, double warnAt, double ordinaryWeekly, double maxOvertimeWeekly,
                         double maxDaily, double maxDailyOvertime, double minRestHours, String legalBasis) {
        public double ceiling() { return ordinaryWeekly + maxOvertimeWeekly; }
        public Thresholds thresholds() { return new Thresholds(warnAt, ordinaryWeekly, ceiling(), minRestHours, maxDaily); }
    }

    private WorkingTimePolicy() {}

    /** The company's configured values (handyflow.security.hours.*) as a policy, used when no policy row applies. */
    public static Policy fallback(Thresholds t, double maxDailyOvertime) {
        return new Policy(null, DEFAULT_CATEGORY, null, t.warnAt(), t.limit(), t.ceiling() - t.limit(), t.maxDaily(), maxDailyOvertime, t.minRestHours(), null);
    }

    public static String category(String raw) {
        if (raw == null) throw bad("Category is required", "INVALID_CATEGORY");
        String c = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!CATEGORY.matcher(c).matches()) throw bad("Category must be 1 to 40 letters, digits or underscores", "INVALID_CATEGORY");
        return c;
    }

    /** True when any value goes beyond the general BCEA figures, so a legal basis must be recorded. */
    public static boolean exceedsGeneralLimits(Policy p) {
        return p.ordinaryWeekly() > BCEA_ORDINARY_WEEKLY || p.maxOvertimeWeekly() > BCEA_OVERTIME_WEEKLY || p.maxDaily() > BCEA_DAILY
                || p.maxDailyOvertime() > BCEA_DAILY_OVERTIME || p.minRestHours() < BCEA_MIN_REST;
    }

    /** Throws 400 with a reason. A new policy must start today or later so that weeks already reported are not rewritten. */
    public static Policy validate(Policy p, LocalDate today) {
        String cat = category(p.category());
        if (p.effectiveFrom() == null) throw bad("Effective date is required", "INVALID_EFFECTIVE_DATE");
        if (p.effectiveFrom().isBefore(today)) throw bad("A policy cannot start in the past", "EFFECTIVE_DATE_PAST");
        for (double v : new double[]{p.warnAt(), p.ordinaryWeekly(), p.maxOvertimeWeekly(), p.maxDaily(), p.maxDailyOvertime(), p.minRestHours()})
            if (Double.isNaN(v) || Double.isInfinite(v)) throw bad("Hours must be numbers", "INVALID_HOURS");
        if (p.ordinaryWeekly() <= 0 || p.ordinaryWeekly() > 84) throw bad("Ordinary weekly hours must be above 0 and at most 84", "INVALID_HOURS");
        if (p.maxOvertimeWeekly() < 0 || p.maxOvertimeWeekly() > 40) throw bad("Weekly overtime must be between 0 and 40", "INVALID_HOURS");
        if (p.warnAt() <= 0 || p.warnAt() > p.ordinaryWeekly()) throw bad("The warning level must be above 0 and not above the ordinary weekly hours", "INVALID_HOURS");
        if (p.maxDaily() <= 0 || p.maxDaily() > 24) throw bad("Daily hours must be above 0 and at most 24", "INVALID_HOURS");
        if (p.maxDailyOvertime() < 0 || p.maxDailyOvertime() > p.maxDaily()) throw bad("Daily overtime cannot be negative or more than the daily hours", "INVALID_HOURS");
        if (p.minRestHours() < 0 || p.minRestHours() > 24) throw bad("Rest between shifts must be between 0 and 24 hours", "INVALID_HOURS");
        String basis = p.legalBasis() == null ? null : p.legalBasis().trim();
        if (basis != null && basis.length() > BASIS_MAX) throw bad("The legal basis is too long (" + BASIS_MAX + " characters at most)", "INVALID_LEGAL_BASIS");
        if (exceedsGeneralLimits(p) && (basis == null || basis.length() < BASIS_MIN))
            throw bad("These limits go beyond the general BCEA figures. Record the legal basis (agreement or determination) that allows it", "LEGAL_BASIS_REQUIRED");
        return new Policy(p.id(), cat, p.effectiveFrom(), p.warnAt(), p.ordinaryWeekly(), p.maxOvertimeWeekly(), p.maxDaily(), p.maxDailyOvertime(), p.minRestHours(),
                basis == null || basis.isEmpty() ? null : basis);
    }

    /**
     * The policy in force for a category in the week starting on weekStart: the latest one for that category that started on or before
     * the week's Monday, else the company's DEFAULT in the same way, else the fallback. A policy dated mid-week first applies the following week.
     */
    public static Policy select(List<Policy> all, String category, LocalDate weekStart, Policy fallback) {
        Policy best = latest(all, category, weekStart);
        if (best == null && !DEFAULT_CATEGORY.equals(category)) best = latest(all, DEFAULT_CATEGORY, weekStart);
        return best != null ? best : fallback;
    }

    private static Policy latest(List<Policy> all, String category, LocalDate weekStart) {
        Policy best = null;
        for (Policy p : all) {
            if (!p.category().equals(category) || p.effectiveFrom() == null || p.effectiveFrom().isAfter(weekStart)) continue;
            if (best == null || p.effectiveFrom().isAfter(best.effectiveFrom())) best = p;
        }
        return best;
    }

    private static HandyFlowException bad(String m, String code) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, code); }
}
