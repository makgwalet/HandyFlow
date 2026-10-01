package za.co.handyflow.platform.agriculture.domain.rules;

import za.co.handyflow.platform.agriculture.dto.AttentionItemResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Severity ladder and ordering for "needs attention" items. Until now only OVERDUE, DUE_TODAY and MEDIUM existed.
 * <p>
 * <pre>
 *   CRITICAL   out of stock; an open HIGH-severity scouting finding
 *   OVERDUE    a due date has passed
 *   DUE_TODAY  due today
 *   UPCOMING   due within the next {@value #UPCOMING_DAYS} days
 *   MEDIUM     below reorder level but not yet out of stock (no due date)
 * </pre>
 * Pure and dependency-free (it only reads the response record) so ranking can be unit tested without Spring.
 */
public final class AgAttentionRules {

    private AgAttentionRules() {}

    public static final String CRITICAL = "CRITICAL";
    public static final String OVERDUE = "OVERDUE";
    public static final String DUE_TODAY = "DUE_TODAY";
    public static final String UPCOMING = "UPCOMING";
    public static final String MEDIUM = "MEDIUM";

    /** How far ahead "upcoming" looks. */
    public static final int UPCOMING_DAYS = 7;

    /** The last date included in the attention window. Queries ask for everything due up to here, then classify each item. */
    public static LocalDate horizon(LocalDate today) {
        return today.plusDays(UPCOMING_DAYS);
    }

    /** 0 is most urgent. An unknown severity sorts last rather than throwing, so a new value can never break the list. */
    public static int rank(String severity) {
        if (severity == null) return 5;
        return switch (severity) {
            case CRITICAL -> 0;
            case OVERDUE -> 1;
            case DUE_TODAY -> 2;
            case UPCOMING -> 3;
            case MEDIUM -> 4;
            default -> 5;
        };
    }

    public static String dueSeverity(LocalDate dueDate, LocalDate today) {
        if (dueDate.isBefore(today)) return OVERDUE;
        if (dueDate.isEqual(today)) return DUE_TODAY;
        return UPCOMING;
    }

    /** Out of stock is urgent; merely below the reorder level is not. */
    public static String stockSeverity(BigDecimal currentQuantity) {
        return currentQuantity != null && currentQuantity.signum() <= 0 ? CRITICAL : MEDIUM;
    }

    /** Most urgent first; within a severity, earliest due date first (items with no date last), then title for a stable order. */
    public static Comparator<AttentionItemResponse> ranking() {
        return Comparator.comparingInt((AttentionItemResponse a) -> rank(a.severity()))
                .thenComparing(a -> a.dueDate() != null ? a.dueDate() : LocalDate.MAX)
                .thenComparing(a -> a.title() != null ? a.title() : "");
    }

    public static List<AttentionItemResponse> ranked(List<AttentionItemResponse> items) {
        List<AttentionItemResponse> sorted = new ArrayList<>(items);
        sorted.sort(ranking());
        return sorted;
    }

    /** Counts per severity in ladder order, omitting severities with no items. */
    public static Map<String, Integer> countBySeverity(List<AttentionItemResponse> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String s : new String[] {CRITICAL, OVERDUE, DUE_TODAY, UPCOMING, MEDIUM}) {
            int n = (int) items.stream().filter(i -> s.equals(i.severity())).count();
            if (n > 0) counts.put(s, n);
        }
        return counts;
    }

    /** Items that need action today or sooner: CRITICAL, OVERDUE or DUE_TODAY. */
    public static boolean isUrgent(AttentionItemResponse item) {
        return rank(item.severity()) <= rank(DUE_TODAY);
    }
}
