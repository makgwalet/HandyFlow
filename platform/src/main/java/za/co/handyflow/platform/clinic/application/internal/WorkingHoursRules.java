package za.co.handyflow.platform.clinic.application.internal;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure rules for weekly working hours: what a valid week is, and whether a booking fits inside it. */
final class WorkingHoursRules {

    static final int MAX_WINDOWS_PER_DAY = 4;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH);
    private static final String[] DAYS = {"Mondays", "Tuesdays", "Wednesdays", "Thursdays", "Fridays", "Saturdays", "Sundays"};

    /** One block of working time on a weekday. dayOfWeek 1 = Monday ... 7 = Sunday. */
    record Window(int dayOfWeek, LocalTime from, LocalTime to) {}

    private WorkingHoursRules() {}

    static LocalTime parse(String hhmm) {
        if (hhmm == null) throw new IllegalArgumentException("Enter times as HH:mm");
        try {
            return LocalTime.parse(hhmm.trim(), HM);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("\"" + hhmm + "\" is not a time. Enter times as HH:mm");
        }
    }

    static String format(LocalTime t) {
        return t.format(HM);
    }

    /** Returns the windows sorted by day then start, or throws IllegalArgumentException for a week that makes no sense. */
    static List<Window> validate(List<Window> windows) {
        if (windows == null) return List.of();
        List<Window> sorted = new ArrayList<>(windows);
        sorted.sort(Comparator.comparingInt(Window::dayOfWeek).thenComparing(Window::from));
        Window prev = null;
        int onDay = 0;
        for (Window w : sorted) {
            if (w.dayOfWeek() < 1 || w.dayOfWeek() > 7) throw new IllegalArgumentException("Day of the week must be 1 (Monday) to 7 (Sunday)");
            if (!w.to().isAfter(w.from())) {
                throw new IllegalArgumentException(dayName(w.dayOfWeek()) + ": the end time must be after the start time");
            }
            if (prev != null && prev.dayOfWeek() == w.dayOfWeek()) {
                onDay++;
                if (onDay > MAX_WINDOWS_PER_DAY) throw new IllegalArgumentException(dayName(w.dayOfWeek()) + ": at most " + MAX_WINDOWS_PER_DAY + " working periods a day");
                if (w.from().isBefore(prev.to())) throw new IllegalArgumentException(dayName(w.dayOfWeek()) + ": working periods overlap");
            } else {
                onDay = 1;
            }
            prev = w;
        }
        return sorted;
    }

    /**
     * Null when the booking fits (or when no hours are set, which means unrestricted); otherwise a sentence
     * saying why not. A booking must sit inside ONE window of its weekday and may not run past midnight.
     */
    static String outsideHours(String practitionerName, List<Window> windows, Instant start, int minutes, ZoneId zone) {
        if (windows == null || windows.isEmpty()) return null;
        ZonedDateTime s = start.atZone(zone);
        ZonedDateTime e = s.plusMinutes(minutes);
        int dow = s.getDayOfWeek().getValue();
        List<Window> today = windows.stream().filter(w -> w.dayOfWeek() == dow).toList();
        boolean sameDay = e.toLocalDate().equals(s.toLocalDate());
        if (sameDay) {
            LocalTime st = s.toLocalTime(), en = e.toLocalTime();
            for (Window w : today) {
                if (!st.isBefore(w.from()) && !en.isAfter(w.to())) return null;
            }
        }
        String who = practitionerName == null || practitionerName.isBlank() ? "This practitioner" : practitionerName;
        String when = s.format(WHEN) + "–" + e.format(HM);
        if (today.isEmpty()) return who + " does not work on " + DAYS[dow - 1] + " (" + when + ").";
        StringBuilder sb = new StringBuilder(who).append(" is outside working hours at ").append(when).append(". Working hours that day: ");
        for (int i = 0; i < today.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(format(today.get(i).from())).append("–").append(format(today.get(i).to()));
        }
        return sb.append(".").toString();
    }

    static String dayName(int dayOfWeek) {
        return dayOfWeek >= 1 && dayOfWeek <= 7 ? DAYS[dayOfWeek - 1] : "Day " + dayOfWeek;
    }
}
