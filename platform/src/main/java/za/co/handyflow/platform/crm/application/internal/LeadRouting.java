package za.co.handyflow.platform.crm.application.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import za.co.handyflow.platform.crm.application.internal.LeadConditions.Condition;
import za.co.handyflow.platform.crm.application.internal.LeadConditions.Facts;

/** Picks who gets a lead: the first matching rule, then its people in turn. Pure. */
final class LeadRouting {
    static final int MAX_ASSIGNEES = 20;

    private LeadRouting() {}

    record Rule(UUID id, int position, Condition condition, List<UUID> assignees, int nextIndex) {}

    /** The rule that matched, the person chosen, and where the rule's pointer moves to. */
    record Decision(UUID ruleId, UUID assignee, int newIndex) {}

    /**
     * `usable` says whether a person can still be assigned (still a user of the account). People who are not usable are skipped, and if nobody in the rule is usable the
     * next matching rule is tried.
     */
    static Optional<Decision> choose(List<Rule> rules, Facts facts, java.util.function.Predicate<UUID> usable) {
        List<Rule> ordered = new ArrayList<>(rules);
        ordered.sort(Comparator.comparingInt(Rule::position).thenComparing(Rule::id));
        for (Rule r : ordered) {
            if (r.assignees().isEmpty() || !LeadConditions.matches(r.condition(), facts)) continue;
            int n = r.assignees().size();
            for (int step = 0; step < n; step++) {
                int idx = Math.floorMod(r.nextIndex() + step, n);
                if (usable.test(r.assignees().get(idx))) return Optional.of(new Decision(r.id(), r.assignees().get(idx), (idx + 1) % n));
            }
        }
        return Optional.empty();
    }

    /** Comma-separated ids as stored. */
    static List<UUID> parseIds(String stored) {
        List<UUID> out = new ArrayList<>();
        if (stored == null || stored.isBlank()) return out;
        for (String s : stored.split(",")) { String t = s.strip(); if (!t.isEmpty()) out.add(UUID.fromString(t)); }
        return out;
    }

    static String joinIds(List<UUID> ids) { return String.join(",", ids.stream().map(UUID::toString).toList()); }

    static void checkRule(String name, Condition condition, List<UUID> assignees) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name the routing rule");
        if (name.strip().length() > 120) throw new IllegalArgumentException("The name can be at most 120 characters");
        if (condition != null) LeadConditions.check(condition);
        if (assignees == null || assignees.isEmpty()) throw new IllegalArgumentException("Choose at least one person to receive the leads");
        if (assignees.size() > MAX_ASSIGNEES) throw new IllegalArgumentException("A rule can have at most " + MAX_ASSIGNEES + " people");
        if (assignees.stream().distinct().count() != assignees.size()) throw new IllegalArgumentException("Each person can be listed once");
    }
}
