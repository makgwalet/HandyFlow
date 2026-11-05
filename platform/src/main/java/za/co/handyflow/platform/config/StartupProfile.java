package za.co.handyflow.platform.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns Spring's startup step timeline into "where did the time go". Steps nest, so a step's
 * own time is its duration minus its children's; ranking by own time points at the real cost.
 * Pure, so it can be tested without starting the application.
 */
public final class StartupProfile {

    /** One recorded step: ids as Spring gives them, parent null for a top-level step. */
    public record Step(long id, Long parentId, String name, String label, Duration duration) { }

    /** A step with its own time worked out. */
    public record Row(String name, String label, Duration total, Duration own) { }

    private StartupProfile() { }

    public static List<Row> rank(List<Step> steps, int limit) {
        Map<Long, Duration> childTime = new HashMap<>();
        for (Step s : steps) {
            if (s.parentId() != null) childTime.merge(s.parentId(), s.duration(), Duration::plus);
        }
        List<Row> rows = new ArrayList<>();
        for (Step s : steps) {
            Duration own = s.duration().minus(childTime.getOrDefault(s.id(), Duration.ZERO));
            if (own.isNegative()) own = Duration.ZERO;
            rows.add(new Row(s.name(), s.label(), s.duration(), own));
        }
        rows.sort(Comparator.comparing(Row::own).reversed());
        return rows.subList(0, Math.min(limit, rows.size()));
    }

    /** Own time added up per kind of step, largest first. */
    public static Map<String, Duration> byKind(List<Step> steps) {
        Map<Long, Duration> childTime = new HashMap<>();
        for (Step s : steps) {
            if (s.parentId() != null) childTime.merge(s.parentId(), s.duration(), Duration::plus);
        }
        Map<String, Duration> byKind = new HashMap<>();
        for (Step s : steps) {
            Duration own = s.duration().minus(childTime.getOrDefault(s.id(), Duration.ZERO));
            if (!own.isNegative()) byKind.merge(s.name(), own, Duration::plus);
        }
        Map<String, Duration> sorted = new java.util.LinkedHashMap<>();
        byKind.entrySet().stream()
                .sorted(Map.Entry.<String, Duration>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }
}
