package za.co.handyflow.platform.catalogue.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure arithmetic for the dashboard: growth by month and items per top-level category. */
public final class DashboardRules {
    private DashboardRules() {}

    public record MonthPoint(String month, long total) {}
    public record CategoryCount(String name, long count) {}

    /** First instant of the oldest month shown (the window is the current month and the {@code months - 1} before it). */
    public static Instant windowStart(Instant now, ZoneId zone, int months) {
        YearMonth ym = YearMonth.from(now.atZone(zone)).minusMonths(months - 1L);
        return ym.atDay(1).atStartOfDay(zone).toInstant();
    }

    /** Running total of items at the end of each month of the window. {@code before} is how many were created before the window. */
    public static List<MonthPoint> monthlyGrowth(List<Instant> createdInWindow, long before, Instant now, ZoneId zone, int months) {
        YearMonth last = YearMonth.from(now.atZone(zone));
        Map<YearMonth, Long> perMonth = new HashMap<>();
        for (Instant c : createdInWindow) perMonth.merge(YearMonth.from(c.atZone(zone)), 1L, Long::sum);
        List<MonthPoint> out = new ArrayList<>();
        long running = before;
        for (int i = months - 1; i >= 0; i--) {
            YearMonth m = last.minusMonths(i);
            running += perMonth.getOrDefault(m, 0L);
            out.add(new MonthPoint(m.toString(), running));
        }
        return out;
    }

    /** Items per top-level category (a sub-category's items count under its root), biggest first; items with no category last. */
    public static List<CategoryCount> byRootCategory(Map<UUID, UUID> parents, Map<UUID, String> names, Map<UUID, Long> countsByCategory, long uncategorised) {
        Map<UUID, Long> perRoot = new HashMap<>();
        countsByCategory.forEach((id, n) -> perRoot.merge(root(id, parents), n, Long::sum));
        List<CategoryCount> out = new ArrayList<>();
        perRoot.forEach((id, n) -> out.add(new CategoryCount(names.getOrDefault(id, "Other"), n)));
        out.sort(Comparator.comparingLong(CategoryCount::count).reversed().thenComparing(CategoryCount::name, String.CASE_INSENSITIVE_ORDER));
        if (uncategorised > 0) out.add(new CategoryCount("No category", uncategorised));
        return out;
    }

    private static UUID root(UUID id, Map<UUID, UUID> parents) {
        UUID cur = id;
        for (int i = 0; i < 20 && parents.get(cur) != null; i++) cur = parents.get(cur);
        return cur;
    }

    /** True when {@code day} is within the last {@code days} days up to and including today. */
    public static boolean withinDays(LocalDate day, LocalDate today, int days) {
        return !day.isAfter(today) && !day.isBefore(today.minusDays(days - 1L));
    }
}
