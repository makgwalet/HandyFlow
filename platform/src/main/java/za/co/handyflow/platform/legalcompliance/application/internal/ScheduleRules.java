package za.co.handyflow.platform.legalcompliance.application.internal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pure rules for report schedules: when the next run falls, and what a valid schedule looks like. */
public final class ScheduleRules {

    private ScheduleRules() {}

    public enum Frequency { WEEKLY, MONTHLY, QUARTERLY }

    public static final int MAX_RECIPIENTS = 25;

    /** Weekly: 1 (Monday) to 7 (Sunday). Monthly and quarterly: 1 to 28, so every month has the day. */
    public static void validateDay(Frequency f, int day) {
        if (f == null) throw new IllegalArgumentException("A frequency is required");
        int max = f == Frequency.WEEKLY ? 7 : 28;
        if (day < 1 || day > max) throw new IllegalArgumentException(f == Frequency.WEEKLY ? "Choose a weekday from 1 (Monday) to 7 (Sunday)" : "Choose a day of the month from 1 to 28");
    }

    /** The first run date strictly after {@code after}. Quarterly runs fall in January, April, July and October. */
    public static LocalDate nextRun(Frequency f, int day, LocalDate after) {
        validateDay(f, day);
        switch (f) {
            case WEEKLY: {
                LocalDate d = after.plusDays(1);
                while (d.getDayOfWeek() != DayOfWeek.of(day)) d = d.plusDays(1);
                return d;
            }
            case MONTHLY: {
                LocalDate d = after.withDayOfMonth(day);
                return d.isAfter(after) ? d : after.plusMonths(1).withDayOfMonth(day);
            }
            default: {
                LocalDate d = after.withDayOfMonth(day);
                for (int i = 0; i < 5; i++) {
                    if (d.isAfter(after) && (d.getMonthValue() - 1) % 3 == 0) return d;
                    d = d.plusMonths(1);
                }
                throw new IllegalStateException("No quarterly date found");
            }
        }
    }

    /** Recipients as given: at least one, no more than the cap, duplicates removed, order kept. */
    public static List<UUID> recipients(List<UUID> ids) {
        Set<UUID> set = new LinkedHashSet<>();
        if (ids != null) for (UUID id : ids) if (id != null) set.add(id);
        if (set.isEmpty()) throw new IllegalArgumentException("Choose at least one person to notify");
        if (set.size() > MAX_RECIPIENTS) throw new IllegalArgumentException("At most " + MAX_RECIPIENTS + " people can be notified by one schedule");
        return List.copyOf(set);
    }

    public static String join(List<UUID> ids) { return String.join(",", ids.stream().map(UUID::toString).toList()); }

    public static List<UUID> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return java.util.Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(UUID::fromString).toList();
    }

    /** The one-line message sent to recipients. It carries the report name and a count, never any of the report's contents. */
    public static String message(String scheduleName, String reportTitle, int rows, boolean truncated) {
        return reportTitle + " — " + rows + " row(s)" + (truncated ? " (cut at the row limit)" : "") + ". Schedule: " + scheduleName + ". Open Reports to view or download it.";
    }
}
