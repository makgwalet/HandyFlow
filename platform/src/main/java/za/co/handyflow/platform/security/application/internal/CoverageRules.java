// security/application/internal/CoverageRules.java
package za.co.handyflow.platform.security.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Pure rules for the coverage board: when a post must be staffed, and where the roster falls short. */
public final class CoverageRules {

    public static final ZoneId SAST = za.co.handyflow.platform.shared.BusinessTime.DEFAULT_ZONE;
    public static final int MAX_DAYS = 14;

    public record Post(UUID id, UUID siteId, String name, Set<Integer> weekdays, LocalTime start, LocalTime end, int minGuards) {}
    /** One day's occurrence of a post. */
    public record Window(Post post, LocalDate day, Instant start, Instant end) {}
    /** A roster slot that counts as cover. */
    public record Cover(Instant start, Instant end) {}
    public record Gap(Instant start, Instant end, int have, int need) {}
    public enum State { COVERED, SHORT, UNCOVERED }
    public record Result(Window window, State state, List<Gap> gaps) {}

    private CoverageRules() {}

    public static int clampDays(Integer asked) {
        if (asked == null) return 7;
        return Math.max(1, Math.min(MAX_DAYS, asked));
    }

    /** Every occurrence of the post on the days [from, from + days). */
    public static List<Window> windows(Post post, LocalDate from, int days) { return windows(post, from, days, SAST); }

    /** The same, with the post's clock times read in the company's zone. */
    public static List<Window> windows(Post post, LocalDate from, int days, ZoneId zone) {
        List<Window> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = from.plusDays(i);
            if (!post.weekdays().contains(d.getDayOfWeek().getValue())) continue;
            Instant s = d.atTime(post.start()).atZone(zone).toInstant();
            LocalDate endDay = post.end().isAfter(post.start()) ? d : d.plusDays(1);
            Instant e = endDay.atTime(post.end()).atZone(zone).toInstant();
            out.add(new Window(post, d, s, e));
        }
        return out;
    }

    /** Where fewer than the needed guards are on the roster during the window. Adjacent stretches with the same headcount are merged. */
    public static Result evaluate(Window w, List<Cover> covers) {
        TreeSet<Instant> cuts = new TreeSet<>();
        cuts.add(w.start()); cuts.add(w.end());
        for (Cover c : covers) {
            if (c.end().isAfter(w.start()) && c.start().isBefore(w.end())) {
                if (c.start().isAfter(w.start())) cuts.add(c.start());
                if (c.end().isBefore(w.end())) cuts.add(c.end());
            }
        }
        List<Instant> pts = new ArrayList<>(cuts);
        List<Gap> gaps = new ArrayList<>();
        int need = w.post().minGuards();
        for (int i = 0; i + 1 < pts.size(); i++) {
            Instant a = pts.get(i), b = pts.get(i + 1);
            int have = 0;
            for (Cover c : covers) if (!c.start().isAfter(a) && !c.end().isBefore(b)) have++;
            if (have < need) {
                Gap last = gaps.isEmpty() ? null : gaps.get(gaps.size() - 1);
                if (last != null && last.end().equals(a) && last.have() == have) gaps.set(gaps.size() - 1, new Gap(last.start(), b, have, need));
                else gaps.add(new Gap(a, b, have, need));
            }
        }
        State s;
        if (gaps.isEmpty()) s = State.COVERED;
        else if (gaps.size() == 1 && gaps.get(0).have() == 0 && gaps.get(0).start().equals(w.start()) && gaps.get(0).end().equals(w.end())) s = State.UNCOVERED;
        else s = State.SHORT;
        return new Result(w, s, gaps);
    }

    public static List<Result> evaluateAll(List<Post> posts, LocalDate from, int days, List<Cover> covers) { return evaluateAll(posts, from, days, covers, SAST); }

    public static List<Result> evaluateAll(List<Post> posts, LocalDate from, int days, List<Cover> covers, ZoneId zone) {
        return posts.stream()
                .flatMap(p -> windows(p, from, days, zone).stream())
                .sorted(Comparator.comparing(Window::start).thenComparing(w -> w.post().name()))
                .map(w -> evaluate(w, covers))
                .toList();
    }

    /** Checks a new post. Returns the weekdays as a sorted set. */
    public static Set<Integer> validate(String name, Set<Integer> weekdays, LocalTime start, LocalTime end, Integer minGuards) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty() || n.length() > 80) throw bad("A post needs a name of up to 80 characters.", "POST_NAME_INVALID");
        if (weekdays == null || weekdays.isEmpty() || weekdays.stream().anyMatch(d -> d == null || d < 1 || d > 7)) {
            throw bad("Pick at least one weekday (1 = Monday to 7 = Sunday).", "POST_DAYS_INVALID");
        }
        if (start == null || end == null) throw bad("Start and end times are required.", "POST_TIME_INVALID");
        if (minGuards == null || minGuards < 1 || minGuards > 50) throw bad("Guards needed must be between 1 and 50.", "POST_GUARDS_INVALID");
        return new TreeSet<>(weekdays);
    }

    private static HandyFlowException bad(String m, String code) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, code); }
}
