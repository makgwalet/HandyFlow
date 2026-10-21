package za.co.handyflow.platform.hr.domain.model;

import java.util.*;

/** Who reports to whom. Pure, so it runs without a database. */
public final class OrgRules {

    private OrgRules() {}

    /**
     * @param managerOf each active employee and their current manager (null for none)
     * @return why {@code manager} cannot be {@code employee}'s manager, or null when it can
     */
    public static String managerProblem(UUID employee, UUID manager, Map<UUID, UUID> managerOf) {
        if (manager == null) return null;
        if (employee.equals(manager)) return "An employee cannot be their own manager.";
        if (!managerOf.containsKey(manager)) return "The manager must be an active employee.";
        UUID cursor = manager;
        for (int i = 0; i <= managerOf.size() && cursor != null; i++) {
            if (cursor.equals(employee)) return "That would make a loop: this employee already manages the chosen manager, directly or through others.";
            cursor = managerOf.get(cursor);
        }
        return null;
    }

    /** {@code root} and everyone who reports to them, however many levels down. */
    public static Set<UUID> team(UUID root, Map<UUID, UUID> managerOf) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        managerOf.forEach((emp, mgr) -> { if (mgr != null) children.computeIfAbsent(mgr, k -> new ArrayList<>()).add(emp); });
        Set<UUID> out = new LinkedHashSet<>();
        Deque<UUID> queue = new ArrayDeque<>(List.of(root));
        while (!queue.isEmpty()) {
            UUID next = queue.poll();
            if (!out.add(next)) continue;
            queue.addAll(children.getOrDefault(next, List.of()));
        }
        return out;
    }
}
