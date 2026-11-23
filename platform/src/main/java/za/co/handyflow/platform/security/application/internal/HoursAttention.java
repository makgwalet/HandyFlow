// security/application/internal/HoursAttention.java
package za.co.handyflow.platform.security.application.internal;

import za.co.handyflow.platform.security.application.internal.WeeklyHoursRules.Assessment;
import za.co.handyflow.platform.security.application.internal.WeeklyHoursRules.State;

import java.util.List;

/** Counts guards, once each, by the worst weekly-hours problem on this week's roster. Pure. */
public final class HoursAttention {

    /** exceeds: past the ceiling. over: past the ordinary limit but within the ceiling. rest: double-booked, short of rest or over the daily limit (and not already counted above). */
    public record Counts(int exceeds, int over, int rest) {
        public static final Counts NONE = new Counts(0, 0, 0);
        public int total() { return exceeds + over + rest; }
    }

    private HoursAttention() {}

    public static Counts count(List<Assessment> guards) {
        int exceeds = 0, over = 0, rest = 0;
        for (Assessment a : guards) {
            if (a.state() == State.EXCEEDS) exceeds++;
            else if (a.state() == State.OVER) over++;
            else if (a.overlaps() || !a.restBreaches().isEmpty() || !a.dayBreaches().isEmpty()) rest++;
        }
        return new Counts(exceeds, over, rest);
    }
}
