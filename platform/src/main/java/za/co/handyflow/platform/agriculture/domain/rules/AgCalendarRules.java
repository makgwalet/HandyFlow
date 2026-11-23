package za.co.handyflow.platform.agriculture.domain.rules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The farm's own calendar, built from dated records the farm already keeps. It adds no dates of its own: a reminder
 * falling due, a scheduled treatment, the day a withdrawal period ends, a birth expected, a planting planned, a harvest
 * expected and a scouting follow-up. Pure and dependency-free so it can be tested without Spring or a database.
 */
public final class AgCalendarRules {

    private AgCalendarRules() {}

    /** A window longer than this is refused: the screen shows a day, a week or a month, and a year of rows is not a calendar. */
    public static final int MAX_DAYS = 62;

    public static final String HEALTH_DUE = "HEALTH_DUE";
    public static final String HEALTH_SCHEDULED = "HEALTH_SCHEDULED";
    public static final String WITHDRAWAL_END = "WITHDRAWAL_END";
    public static final String BIRTH_DUE = "BIRTH_DUE";
    public static final String PLANTING = "PLANTING";
    public static final String HARVEST = "HARVEST";
    public static final String SCOUTING_FOLLOWUP = "SCOUTING_FOLLOWUP";

    /** Things a person has to do. Only these are flagged overdue when their day has passed. */
    private static final List<String> ACTIONS = List.of(HEALTH_DUE, HEALTH_SCHEDULED, SCOUTING_FOLLOWUP);
    private static final List<String> ORDER = List.of(HEALTH_DUE, HEALTH_SCHEDULED, WITHDRAWAL_END, BIRTH_DUE, PLANTING, HARVEST, SCOUTING_FOLLOWUP);

    public record Item(LocalDate date, String kind, String title, String detail, String targetType, UUID targetId) {}

    /** One treatment as the calendar needs it. {@code scheduled} treatments have not been given, so nothing is withheld yet. */
    public record Treatment(LocalDate date, boolean scheduled, Integer meatDays, Integer milkDays, Integer eggDays) {}

    public record WithdrawalEnd(LocalDate clearOn, String what) {}

    public static void requireWindow(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new IllegalArgumentException("from and to dates are required");
        if (to.isBefore(from)) throw new IllegalArgumentException("The end date cannot be before the start date");
        if (from.plusDays(MAX_DAYS - 1L).isBefore(to)) throw new IllegalArgumentException("Choose a window of " + MAX_DAYS + " days or less");
    }

    public static boolean inWindow(LocalDate date, LocalDate from, LocalDate to) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    /** The first day clear of each period (meat, milk, eggs) of a given treatment that lands inside the window. A scheduled treatment has none. */
    public static List<WithdrawalEnd> withdrawalEnds(Treatment t, LocalDate from, LocalDate to) {
        List<WithdrawalEnd> out = new ArrayList<>();
        if (t == null || t.scheduled()) return out;
        add(out, "meat", AgWithdrawalRules.clearOn(t.date(), t.meatDays()), from, to);
        add(out, "milk", AgWithdrawalRules.clearOn(t.date(), t.milkDays()), from, to);
        add(out, "eggs", AgWithdrawalRules.clearOn(t.date(), t.eggDays()), from, to);
        return out;
    }

    private static void add(List<WithdrawalEnd> out, String what, LocalDate clearOn, LocalDate from, LocalDate to) {
        if (inWindow(clearOn, from, to)) out.add(new WithdrawalEnd(clearOn, what));
    }

    /** True for something to be done whose day has passed. Withdrawals ending, births expected and the like are not "overdue". */
    public static boolean isOverdue(String kind, LocalDate date, LocalDate today) {
        return ACTIONS.contains(kind) && date != null && today != null && date.isBefore(today);
    }

    /** By day, then in a fixed kind order, then by title, so the same data always reads the same way. */
    public static List<Item> sorted(List<Item> items) {
        List<Item> out = new ArrayList<>(items);
        out.sort(Comparator.comparing(Item::date)
                .thenComparing(i -> ORDER.indexOf(i.kind()))
                .thenComparing(Item::title, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** "VACCINATION" or "tick_control" becomes "Vaccination" or "Tick control". Blank gives "Treatment". */
    public static String humanize(String code) {
        if (code == null || code.isBlank()) return "Treatment";
        String s = code.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
