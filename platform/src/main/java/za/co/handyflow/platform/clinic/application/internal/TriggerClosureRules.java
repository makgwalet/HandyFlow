package za.co.handyflow.platform.clinic.application.internal;

import java.util.*;
import java.util.function.Function;

/**
 * Question groups a consultation's answers have opened (TRIGGER_GROUP rules) that the clinician has not started (patch 0186).
 * Follows triggers through groups that were themselves opened, once each, so a loop between two groups ends.
 */
final class TriggerClosureRules {
    private TriggerClosureRules() {}

    /** Codes reachable from the started groups through trigger rules, not started, in the order they were reached. */
    static List<String> unstarted(Collection<String> started, Function<String, List<String>> triggersOf) {
        Set<String> seen = new LinkedHashSet<>(started);
        Deque<String> todo = new ArrayDeque<>(started);
        List<String> out = new ArrayList<>();
        while (!todo.isEmpty()) {
            String code = todo.poll();
            List<String> next = triggersOf.apply(code);
            if (next == null) continue;
            for (String t : next) {
                if (t == null || !seen.add(t)) continue;
                out.add(t);
                todo.add(t);
            }
        }
        return out;
    }
}
