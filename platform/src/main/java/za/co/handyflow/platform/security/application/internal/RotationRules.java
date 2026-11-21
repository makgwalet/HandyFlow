package za.co.handyflow.platform.security.application.internal;

import za.co.handyflow.platform.security.domain.model.RotationPattern.PatternType;

import java.time.DayOfWeek;
import java.util.Map;
import java.util.Optional;

/**
 * Which shift, if any, a guard works on a given day of a rotation. Pure.
 *
 * Before this, ALTERNATING_DAY_NIGHT put every guard on duty every day at the
 * same start hour (so it never produced a night shift), WEEKLY_FIXED started
 * its NIGHT days at the day hour, and CUSTOM put guards on duty every day.
 */
final class RotationRules {

    static final int DEFAULT_DAY_HOUR = 6;
    static final int DEFAULT_NIGHT_HOUR = 18;

    record Slot(int hour, int minute) {}

    private RotationRules() {}

    /**
     * @param daysSinceStart days from the assignment's start date plus the guard's position in the cycle (may be any size)
     * @return the start time of the shift, or empty on an off day (and always for CUSTOM, which cannot be generated)
     */
    static Optional<Slot> slot(PatternType type, Map<String, Object> def, long daysSinceStart, DayOfWeek dow) {
        Map<String, Object> d = def == null ? Map.of() : def;
        return switch (type) {
            case FIXED_DAYS_ON_OFF -> {
                int on = Math.max(1, intVal(d, "onDays", 4));
                int off = Math.max(0, intVal(d, "offDays", 2));
                boolean working = Math.floorMod(daysSinceStart, (long) (on + off)) < on;
                yield working ? Optional.of(time(d, "startHour", "dayStart", DEFAULT_DAY_HOUR)) : Optional.empty();
            }
            case WEEKLY_FIXED -> {
                Object v = d.get(dow.name().toLowerCase());
                String kind = v == null ? "OFF" : v.toString().trim().toUpperCase();
                yield switch (kind) {
                    case "OFF", "" -> Optional.empty();
                    case "NIGHT" -> Optional.of(time(d, null, "nightStart", DEFAULT_NIGHT_HOUR));
                    default -> Optional.of(time(d, "startHour", "dayStart", DEFAULT_DAY_HOUR));
                };
            }
            case ALTERNATING_DAY_NIGHT -> {
                int weeks = Math.max(1, intVal(d, "cycleWeeks", 2));
                long week = Math.floorMod(Math.floorDiv(daysSinceStart, 7L), (long) weeks);
                // First half of the cycle on days, the rest on nights. One week cycle is all days.
                boolean night = weeks > 1 && week >= (weeks + 1) / 2;
                yield Optional.of(night
                        ? time(d, null, "nightStart", DEFAULT_NIGHT_HOUR)
                        : time(d, "startHour", "dayStart", DEFAULT_DAY_HOUR));
            }
            case CUSTOM -> Optional.empty();
        };
    }

    /** "HH:mm" under textKey, else a whole hour under hourKey, else the default hour. Bad values fall back. */
    private static Slot time(Map<String, Object> d, String hourKey, String textKey, int defaultHour) {
        Object t = textKey == null ? null : d.get(textKey);
        if (t instanceof String s) {
            String[] p = s.trim().split(":");
            try {
                int h = Integer.parseInt(p[0]);
                int m = p.length > 1 ? Integer.parseInt(p[1]) : 0;
                if (h >= 0 && h <= 23 && m >= 0 && m <= 59) return new Slot(h, m);
            } catch (NumberFormatException ignored) { /* use the fallback */ }
        }
        int h = hourKey == null ? defaultHour : intVal(d, hourKey, defaultHour);
        return new Slot(h >= 0 && h <= 23 ? h : defaultHour, 0);
    }

    private static int intVal(Map<String, Object> m, String key, int dflt) {
        Object v = m.get(key);
        return v instanceof Number n ? n.intValue() : dflt;
    }
}
