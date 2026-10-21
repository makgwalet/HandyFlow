package za.co.handyflow.platform.crm.application.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Company hierarchy rules over a map of child id to parent id (only companies that have a parent appear).
 * Free of Spring and the database so the loop and depth checks can be tested alone.
 */
final class HierarchyRules {
    /** A top company is level 1; a hierarchy can go five levels deep. */
    static final int MAX_LEVELS = 5;

    private HierarchyRules() {}

    /** Ancestors of a company, nearest parent first. Stops if the data already contains a loop. */
    static List<UUID> ancestors(UUID id, Map<UUID, UUID> parentOf) {
        List<UUID> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        seen.add(id);
        UUID cur = parentOf.get(id);
        while (cur != null && seen.add(cur)) { out.add(cur); cur = parentOf.get(cur); }
        return out;
    }

    /** Every company below this one, any depth. */
    static Set<UUID> descendants(UUID id, Map<UUID, UUID> parentOf) {
        Map<UUID, List<UUID>> kids = new HashMap<>();
        parentOf.forEach((child, parent) -> kids.computeIfAbsent(parent, k -> new ArrayList<>()).add(child));
        Set<UUID> out = new LinkedHashSet<>();
        List<UUID> queue = new ArrayList<>(kids.getOrDefault(id, List.of()));
        while (!queue.isEmpty()) {
            UUID next = queue.remove(0);
            if (next.equals(id) || !out.add(next)) continue;
            queue.addAll(kids.getOrDefault(next, List.of()));
        }
        return out;
    }

    /** Levels in the sub-tree rooted at id, counting id itself (a company with no branches is 1). */
    static int height(UUID id, Map<UUID, UUID> parentOf) {
        Map<UUID, List<UUID>> kids = new HashMap<>();
        parentOf.forEach((child, parent) -> kids.computeIfAbsent(parent, k -> new ArrayList<>()).add(child));
        return height(id, kids, new HashSet<>());
    }

    private static int height(UUID id, Map<UUID, List<UUID>> kids, Set<UUID> path) {
        if (!path.add(id)) return 0;
        int best = 0;
        for (UUID k : kids.getOrDefault(id, List.of())) best = Math.max(best, height(k, kids, path));
        path.remove(id);
        return best + 1;
    }

    /** Throws when putting child under newParent would be wrong. A null newParent (make it a top company) is always fine. */
    static void checkParent(UUID child, UUID newParent, Map<UUID, UUID> parentOf) {
        if (newParent == null) return;
        if (child.equals(newParent)) throw new IllegalArgumentException("A company cannot be its own parent");
        if (descendants(child, parentOf).contains(newParent)) {
            throw new IllegalArgumentException("That company is one of this company's own branches, so it cannot be its parent");
        }
        int levelsAbove = ancestors(newParent, parentOf).size() + 1;   // the new parent and everything above it
        int levelsBelow = height(child, parentOf);                     // this company and its branches
        if (levelsAbove + levelsBelow > MAX_LEVELS) {
            throw new IllegalArgumentException("A hierarchy can be at most " + MAX_LEVELS + " levels deep");
        }
    }

    /** Nearest-first ancestors turned root-first, for breadcrumbs. */
    static List<UUID> rootFirst(List<UUID> nearestFirst) {
        List<UUID> copy = new ArrayList<>(nearestFirst);
        Collections.reverse(copy);
        return copy;
    }
}
