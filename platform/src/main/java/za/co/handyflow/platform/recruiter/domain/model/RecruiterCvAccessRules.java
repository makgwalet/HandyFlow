package za.co.handyflow.platform.recruiter.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Who has been opening candidates' CVs, and whether anyone has opened an unusual number. Pure. A person reading CVs for a live vacancy opens a few
 * an hour; opening dozens in an hour is what a bulk copy of candidate data looks like, so it is flagged for an administrator to look at (it is not
 * blocked).
 */
public final class RecruiterCvAccessRules {
    private RecruiterCvAccessRules() {}

    public static final int FLAG_PER_HOUR = 30, MAX_DAYS = 365, DEFAULT_DAYS = 30;

    public record View(String actorKey, String actorName, UUID applicationId, Instant at) {}

    public record Row(String actorName, int views, int candidates, Instant busiestHour, int busiestHourViews, boolean flagged) {}

    public static String daysProblem(Integer days) {
        if (days == null) return null;
        return days < 1 || days > MAX_DAYS ? "Choose between 1 and " + MAX_DAYS + " days." : null;
    }

    public static int daysOrDefault(Integer days) { return days == null ? DEFAULT_DAYS : days; }

    /** One row per person, the people with the most views first. */
    public static List<Row> summarise(List<View> views) {
        Map<String, List<View>> byActor = new LinkedHashMap<>();
        for (View v : views) byActor.computeIfAbsent(v.actorKey() == null ? "?" : v.actorKey(), k -> new ArrayList<>()).add(v);
        List<Row> rows = new ArrayList<>();
        for (List<View> mine : byActor.values()) {
            Map<Instant, Integer> perHour = new HashMap<>();
            Set<UUID> candidates = new HashSet<>();
            for (View v : mine) {
                perHour.merge(v.at().truncatedTo(ChronoUnit.HOURS), 1, Integer::sum);
                if (v.applicationId() != null) candidates.add(v.applicationId());
            }
            Map.Entry<Instant, Integer> busiest = perHour.entrySet().stream()
                    .max(Map.Entry.<Instant, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey())).orElseThrow();
            String name = mine.get(0).actorName() == null || mine.get(0).actorName().isBlank() ? "Unknown" : mine.get(0).actorName();
            rows.add(new Row(name, mine.size(), candidates.size(), busiest.getKey(), busiest.getValue(), busiest.getValue() >= FLAG_PER_HOUR));
        }
        rows.sort(Comparator.comparingInt(Row::views).reversed().thenComparing(Row::actorName));
        return rows;
    }
}
