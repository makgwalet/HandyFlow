// security/application/internal/MonthlyPackRules.java
package za.co.handyflow.platform.security.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.security.application.internal.CoverageRules.Cover;
import za.co.handyflow.platform.security.application.internal.CoverageRules.Gap;
import za.co.handyflow.platform.security.application.internal.CoverageRules.Post;
import za.co.handyflow.platform.security.application.internal.CoverageRules.Result;
import za.co.handyflow.platform.security.application.internal.CoverageRules.State;
import za.co.handyflow.platform.security.application.internal.CoverageRules.Window;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Pure rules for the monthly service report: the South African month, and cover delivered against posts. */
public final class MonthlyPackRules {

    public static final java.time.ZoneId SAST = CoverageRules.SAST;

    /** Cover figures for the posts that fell due in the period. Hours are guard-hours. */
    public record CoverFigures(int windows, double requiredHours, double coveredHours, int windowsShort, int windowsUncovered) {}

    private MonthlyPackRules() {}

    /** The month asked for ("2026-10"), or the month before the current one. A future month is refused. */
    public static YearMonth month(String asked, Instant now) {
        YearMonth current = YearMonth.from(now.atZone(SAST));
        if (asked == null || asked.isBlank()) return current.minusMonths(1);
        YearMonth m;
        try { m = YearMonth.parse(asked.strip()); }
        catch (java.time.format.DateTimeParseException e) {
            throw new HandyFlowException("Month must look like 2026-10.", HttpStatus.BAD_REQUEST, "PACK_MONTH_INVALID");
        }
        if (m.isAfter(current)) throw new HandyFlowException("That month has not started yet.", HttpStatus.BAD_REQUEST, "PACK_FUTURE_MONTH");
        return m;
    }

    /** [start, end) of the South African month. */
    public static Instant[] window(YearMonth m) {
        return new Instant[]{m.atDay(1).atStartOfDay(SAST).toInstant(), m.plusMonths(1).atDay(1).atStartOfDay(SAST).toInstant()};
    }

    public static boolean inProgress(YearMonth m, Instant now) { return YearMonth.from(now.atZone(SAST)).equals(m); }

    /**
     * Cover delivered against the posts, for post-days that had finished by {@code until} (the end of the month, or now for
     * the current month). Required = hours of each finished post-day times guards needed. Covered = required minus the
     * guard-hours missing in each short stretch.
     */
    public static CoverFigures cover(List<Post> posts, YearMonth m, Instant until, List<Cover> covers) {
        int windows = 0, shortW = 0, noneW = 0;
        double required = 0, missing = 0;
        for (Post p : posts) {
            for (Window w : CoverageRules.windows(p, m.atDay(1), m.lengthOfMonth())) {
                if (w.end().isAfter(until)) continue;
                Result r = CoverageRules.evaluate(w, covers);
                windows++;
                required += hours(w.start(), w.end()) * p.minGuards();
                for (Gap g : r.gaps()) missing += hours(g.start(), g.end()) * (g.need() - g.have());
                if (r.state() == State.SHORT) shortW++;
                if (r.state() == State.UNCOVERED) noneW++;
            }
        }
        return new CoverFigures(windows, round1(required), round1(required - missing), shortW, noneW);
    }

    /** A percentage with one decimal, or null when there is nothing to measure against. */
    public static Double pct(double part, double whole) {
        if (whole <= 0) return null;
        return round1(part * 100.0 / whole);
    }

    static double hours(Instant a, Instant b) { return Duration.between(a, b).getSeconds() / 3600.0; }

    static double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    /** The days of the month that count for the Occurrence Book: all of a past month, up to today for the current one. */
    public static int daysInPeriod(YearMonth m, Instant now) {
        LocalDate today = now.atZone(SAST).toLocalDate();
        if (YearMonth.from(today).equals(m)) return today.getDayOfMonth();
        return m.lengthOfMonth();
    }
}
