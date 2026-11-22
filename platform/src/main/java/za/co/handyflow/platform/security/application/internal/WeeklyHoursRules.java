// security/application/internal/WeeklyHoursRules.java
package za.co.handyflow.platform.security.application.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure rules for the weekly hours warning: hours rostered in a South African week, double bookings and short rests. */
public final class WeeklyHoursRules {

    public static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    /** Defaults: warn from 40 h, 45 h ordinary limit, 55 h ceiling (45 + 10 overtime), 12 h rest between shifts. */
    public record Thresholds(double warnAt, double limit, double ceiling, double minRestHours) {
        public static final Thresholds DEFAULTS = new Thresholds(40, 45, 55, 12);
    }

    public record Slot(Instant start, Instant end) {}
    public record RestBreach(Instant from, Instant to, double hours) {}
    public enum State { OK, NEAR, OVER, EXCEEDS }
    public record Assessment(double hours, State state, int shifts, boolean overlaps, List<RestBreach> restBreaches) {
        public boolean flagged() { return state != State.OK || overlaps || !restBreaches.isEmpty(); }
    }

    private WeeklyHoursRules() {}

    /** Monday 00:00 to the next Monday 00:00, South African time, of the week containing the day. */
    public static Instant[] week(LocalDate anyDay) {
        LocalDate mon = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new Instant[]{mon.atStartOfDay(SAST).toInstant(), mon.plusDays(7).atStartOfDay(SAST).toInstant()};
    }

    public static State stateFor(double hours, Thresholds t) {
        if (hours > t.ceiling()) return State.EXCEEDS;
        if (hours > t.limit()) return State.OVER;
        if (hours >= t.warnAt()) return State.NEAR;
        return State.OK;
    }

    /**
     * One guard's week. Overlapping or touching slots are merged first, so a double booking is not counted twice
     * (it is flagged instead). Slots from just before the week may be included: they only matter for the rest check.
     */
    public static Assessment assess(List<Slot> slots, Instant weekStart, Instant weekEnd, Thresholds t) {
        List<Slot> sorted = slots.stream().filter(s -> s.end().isAfter(s.start())).sorted(Comparator.comparing(Slot::start)).toList();
        List<Slot> merged = new ArrayList<>();
        boolean overlaps = false;
        for (Slot s : sorted) {
            if (!merged.isEmpty()) {
                Slot last = merged.get(merged.size() - 1);
                if (!s.start().isAfter(last.end())) {
                    if (s.start().isBefore(last.end())) overlaps = true;
                    merged.set(merged.size() - 1, new Slot(last.start(), s.end().isAfter(last.end()) ? s.end() : last.end()));
                    continue;
                }
            }
            merged.add(s);
        }
        double seconds = 0;
        int inWeek = 0;
        for (Slot s : merged) {
            Instant a = s.start().isBefore(weekStart) ? weekStart : s.start();
            Instant b = s.end().isAfter(weekEnd) ? weekEnd : s.end();
            if (b.isAfter(a)) seconds += Duration.between(a, b).getSeconds();
        }
        for (Slot s : sorted) if (s.end().isAfter(weekStart) && s.start().isBefore(weekEnd)) inWeek++;
        List<RestBreach> breaches = new ArrayList<>();
        for (int i = 0; i + 1 < merged.size(); i++) {
            Slot a = merged.get(i), b = merged.get(i + 1);
            double rest = Duration.between(a.end(), b.start()).getSeconds() / 3600.0;
            boolean nextInWeek = !b.start().isBefore(weekStart) && b.start().isBefore(weekEnd);
            if (nextInWeek && rest < t.minRestHours()) breaches.add(new RestBreach(a.end(), b.start(), round1(rest)));
        }
        double hours = round1(seconds / 3600.0);
        return new Assessment(hours, stateFor(seconds / 3600.0, t), inWeek, overlaps, List.copyOf(breaches));
    }

    static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}
