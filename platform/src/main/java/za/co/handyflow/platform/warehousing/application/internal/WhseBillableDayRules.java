package za.co.handyflow.platform.warehousing.application.internal;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which calendar days storage is billed. The standard is every day, inclusive (ALL_DAYS): arrival day, dispatch day, weekends and public
 * holidays bill, and a part day bills in full. A contract may replace that for a client, or for the whole warehouse. Pure: no database.
 */
public final class WhseBillableDayRules {
    private WhseBillableDayRules() {}

    public enum DayRule {
        ALL_DAYS("Every calendar day (standard)"),
        WEEKDAYS_ONLY("Monday to Friday only"),
        WEEKDAYS_NO_HOLIDAYS("Monday to Friday, except public holidays");
        public final String label;
        DayRule(String label) { this.label = label; }
    }

    public record RuleVersion(UUID clientId, DayRule rule, LocalDate effectiveFrom, Instant createdAt) {}

    public static DayRule parse(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Choose a billable-day rule.");
        try { return DayRule.valueOf(s.strip().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown billable-day rule " + s.strip() + "."); }
    }

    /** The rule for a client on a day: the client's own newest version that has started, else the warehouse default's, else the standard. */
    public static DayRule ruleFor(List<RuleVersion> versions, UUID clientId, LocalDate date) {
        DayRule own = clientId == null ? null : newest(versions, clientId, date);
        if (own != null) return own;
        DayRule dflt = newest(versions, null, date);
        return dflt != null ? dflt : DayRule.ALL_DAYS;
    }

    private static DayRule newest(List<RuleVersion> versions, UUID clientId, LocalDate date) {
        return versions.stream()
                .filter(v -> java.util.Objects.equals(v.clientId(), clientId) && !v.effectiveFrom().isAfter(date))
                .max(Comparator.comparing(RuleVersion::effectiveFrom).thenComparing(RuleVersion::createdAt))
                .map(RuleVersion::rule).orElse(null);
    }

    public static boolean isBillable(DayRule rule, LocalDate date, Set<LocalDate> holidays) {
        boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        return switch (rule) {
            case ALL_DAYS -> true;
            case WEEKDAYS_ONLY -> !weekend;
            case WEEKDAYS_NO_HOLIDAYS -> !weekend && !holidays.contains(date);
        };
    }

    /** A new rule applies from today or later: days already accrued are not rewritten. */
    public static void checkEffectiveFrom(LocalDate effectiveFrom, LocalDate today) {
        if (effectiveFrom == null) throw new IllegalArgumentException("Choose the date the rule starts.");
        if (effectiveFrom.isBefore(today)) throw new IllegalArgumentException("A billable-day rule cannot start in the past, because those days have already been billed.");
    }
}
