package za.co.handyflow.platform.projects.application.internal.schedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Pure scheduling logic — no Spring, no database, no clock. Everything is passed in,
 * so every rule is unit-testable.
 *
 * Three operations:
 *  - forecast():   critical-path forecast from "today" (the data date), honouring all four
 *                  dependency types with lag, progress, working calendar and holidays.
 *  - violations(): dependencies the CURRENT planned dates break.
 *  - cascade():    forward shifts that make an edited task's descendants respect their
 *                  dependencies again (never pulls anything earlier).
 *  - wouldCreateCycle(): used before a dependency is saved.
 *
 * DEPENDENCY SEMANTICS (lag in working days, never negative):
 *   FS  successor starts   ≥ predecessor finish + 1 + lag
 *   SS  successor starts   ≥ predecessor start  + lag
 *   FF  successor finishes ≥ predecessor finish + lag
 *   SF  successor finishes ≥ predecessor start  + lag
 *
 * FORECAST MODEL
 *  - COMPLETED tasks are fixed at their actual dates and carry no float.
 *  - Not-started tasks cannot start before the data date.
 *  - Started tasks keep their actual start; their REMAINING work (duration × unfinished %)
 *    runs from the data date.
 *  - Total float = latest finish − forecast finish. A task is critical when float ≤ 0.
 *  - Tasks without both planned dates, and CANCELLED tasks, are left out of the model.
 */
public final class ScheduleEngine {

    private ScheduleEngine() {}

    // ── Input / output types ──────────────────────────────────────────────────

    public record Node(UUID id, LocalDate plannedStart, LocalDate plannedEnd,
                       LocalDate actualStart, LocalDate actualEnd,
                       String status, BigDecimal progressPct) {
        boolean completed() { return "COMPLETED".equals(status); }
        boolean cancelled() { return "CANCELLED".equals(status); }
        boolean started() {
            return "IN_PROGRESS".equals(status)
                    || actualStart != null
                    || (progressPct != null && progressPct.signum() > 0);
        }
    }

    public record Dep(UUID id, UUID predecessorId, UUID successorId, String type, int lagDays) {}

    public record NodeResult(UUID id, LocalDate start, LocalDate end,
                             Integer floatDays, boolean critical, boolean fixed) {}

    /** cyclic=true → results is empty and unresolvedIds lists tasks in or downstream of a cycle. */
    public record Forecast(boolean cyclic, List<UUID> unresolvedIds,
                           Map<UUID, NodeResult> results, LocalDate finish) {}

    public record Violation(UUID dependencyId, UUID predecessorId, UUID successorId,
                            String type, int lagDays, int shortfallWorkingDays) {}

    public record Shift(UUID taskId, LocalDate oldStart, LocalDate oldEnd,
                        LocalDate newStart, LocalDate newEnd, int shiftWorkingDays) {}

    /** blocked = descendants that need to move but are completed/started, so were left alone. */
    public record Cascade(boolean cyclic, List<Shift> shifts, List<UUID> blocked) {}

    public static boolean schedulable(Node n) {
        return !n.cancelled() && n.plannedStart() != null && n.plannedEnd() != null;
    }

    // ── Cycle check ───────────────────────────────────────────────────────────

    /**
     * Would adding predecessor → successor create a cycle? A cycle appears exactly when a path
     * already leads from successor back to predecessor. Returns that path
     * (successor … predecessor) or empty when the new dependency is safe.
     */
    public static Optional<List<UUID>> wouldCreateCycle(Collection<Dep> existing, UUID predecessor, UUID successor) {
        if (predecessor.equals(successor)) return Optional.of(List.of(predecessor));
        Map<UUID, List<UUID>> next = new HashMap<>();
        for (Dep d : existing) next.computeIfAbsent(d.predecessorId(), k -> new ArrayList<>()).add(d.successorId());

        Deque<List<UUID>> stack = new ArrayDeque<>();
        stack.push(List.of(successor));
        Set<UUID> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            List<UUID> path = stack.pop();
            UUID last = path.get(path.size() - 1);
            if (last.equals(predecessor)) return Optional.of(path);
            if (!seen.add(last)) continue;
            for (UUID n : next.getOrDefault(last, List.of())) {
                List<UUID> p = new ArrayList<>(path);
                p.add(n);
                stack.push(p);
            }
        }
        return Optional.empty();
    }

    // ── Forecast + critical path ──────────────────────────────────────────────

    private static final class St { int es, ef, rem, ls, lf; boolean fixed; }

    public static Forecast forecast(List<Node> allNodes, List<Dep> allDeps,
                                    SimpleWorkingCalendar cal, LocalDate dataDate) {
        Model m = model(allNodes, allDeps);
        if (m.byId().isEmpty()) return new Forecast(false, List.of(), Map.of(), null);

        List<UUID> order = topo(m);
        if (order.size() < m.byId().size()) {
            Set<UUID> done = new HashSet<>(order);
            List<UUID> unresolved = m.byId().keySet().stream().filter(id -> !done.contains(id)).toList();
            return new Forecast(true, unresolved, Map.of(), null);
        }

        LocalDate earliest = dataDate;
        for (Node n : m.byId().values()) {
            if (n.plannedStart().isBefore(earliest)) earliest = n.plannedStart();
            if (n.actualStart() != null && n.actualStart().isBefore(earliest)) earliest = n.actualStart();
        }
        WorkDayIndex ix = new WorkDayIndex(cal, earliest);
        int dataIdx = ix.indexOf(dataDate);

        Map<UUID, St> st = new HashMap<>();

        // ── Forward pass ──
        for (UUID id : order) {
            Node n = m.byId().get(id);
            St s = new St();
            int planStart = ix.indexOf(n.plannedStart());
            int planEnd   = Math.max(planStart, ix.indexOfEnd(n.plannedEnd()));
            int dur       = planEnd - planStart + 1;

            if (n.completed()) {
                s.fixed = true;
                s.es = ix.indexOf(n.actualStart() != null ? n.actualStart() : n.plannedStart());
                s.ef = Math.max(s.es, ix.indexOfEnd(n.actualEnd() != null ? n.actualEnd() : n.plannedEnd()));
                s.rem = s.ef - s.es + 1;
                st.put(id, s);
                continue;
            }

            double pct = n.progressPct() == null ? 0 : Math.max(0, Math.min(100, n.progressPct().doubleValue()));
            boolean started = n.started();
            s.rem = started ? Math.max(1, (int) Math.ceil(dur * (100 - pct) / 100.0)) : dur;

            int startLB  = started ? dataIdx : Math.max(planStart, dataIdx);
            int finishLB = Integer.MIN_VALUE;
            for (Dep d : m.in().getOrDefault(id, List.of())) {
                St p = st.get(d.predecessorId());
                switch (d.type()) {
                    case "SS" -> startLB  = Math.max(startLB,  p.es + d.lagDays());
                    case "FF" -> finishLB = Math.max(finishLB, p.ef + d.lagDays());
                    case "SF" -> finishLB = Math.max(finishLB, p.es + d.lagDays());
                    default   -> startLB  = Math.max(startLB,  p.ef + 1 + d.lagDays());   // FS
                }
            }

            if (started) {
                s.es = ix.indexOf(n.actualStart() != null ? n.actualStart() : n.plannedStart());
                if (n.actualStart() == null) s.es = Math.min(s.es, dataIdx);
                s.ef = Math.max(startLB + s.rem - 1, finishLB);
            } else {
                s.es = startLB;
                if (finishLB != Integer.MIN_VALUE) s.es = Math.max(s.es, finishLB - s.rem + 1);
                s.ef = s.es + s.rem - 1;
            }
            st.put(id, s);
        }

        int projectFinish = st.values().stream().mapToInt(x -> x.ef).max().orElse(0);

        // ── Backward pass (only for tasks that can still move) ──
        for (int i = order.size() - 1; i >= 0; i--) {
            UUID id = order.get(i);
            St s = st.get(id);
            if (s.fixed) continue;
            int lf = projectFinish;
            for (Dep d : m.out().getOrDefault(id, List.of())) {
                St succ = st.get(d.successorId());
                if (succ.fixed) continue;
                int cand = switch (d.type()) {
                    case "SS" -> (succ.ls - d.lagDays()) + s.rem - 1;
                    case "FF" -> succ.lf - d.lagDays();
                    case "SF" -> (succ.lf - d.lagDays()) + s.rem - 1;
                    default   -> succ.ls - 1 - d.lagDays();                 // FS
                };
                lf = Math.min(lf, cand);
            }
            s.lf = lf;
            s.ls = lf - s.rem + 1;
        }

        Map<UUID, NodeResult> results = new LinkedHashMap<>();
        for (UUID id : m.byId().keySet()) {
            St s = st.get(id);
            Integer fl = s.fixed ? null : s.lf - s.ef;
            boolean critical = !s.fixed && fl <= 0;
            results.put(id, new NodeResult(id, ix.dateOf(s.es), ix.dateOf(s.ef), fl, critical, s.fixed));
        }
        return new Forecast(false, List.of(), results, ix.dateOf(projectFinish));
    }

    // ── Violations against CURRENT planned dates ──────────────────────────────

    public static List<Violation> violations(List<Node> allNodes, List<Dep> allDeps, SimpleWorkingCalendar cal) {
        Model m = model(allNodes, allDeps);
        if (m.byId().isEmpty()) return List.of();

        LocalDate earliest = m.byId().values().stream().map(Node::plannedStart).min(Comparator.naturalOrder()).orElseThrow();
        WorkDayIndex ix = new WorkDayIndex(cal, earliest);

        List<Violation> out = new ArrayList<>();
        for (Dep d : m.deps()) {
            Node pred = m.byId().get(d.predecessorId());
            Node succ = m.byId().get(d.successorId());
            if (succ.completed()) continue;                       // history, not a planning problem

            int ps = ix.indexOf(pred.plannedStart()), pe = Math.max(ps, ix.indexOfEnd(pred.plannedEnd()));
            int ss = ix.indexOf(succ.plannedStart()), se = Math.max(ss, ix.indexOfEnd(succ.plannedEnd()));

            int required, actual;
            switch (d.type()) {
                case "SS" -> { required = ps + d.lagDays();     actual = ss; }
                case "FF" -> { required = pe + d.lagDays();     actual = se; }
                case "SF" -> { required = ps + d.lagDays();     actual = se; }
                default   -> { required = pe + 1 + d.lagDays(); actual = ss; }   // FS
            }
            if (actual < required) {
                out.add(new Violation(d.id(), d.predecessorId(), d.successorId(),
                        d.type(), d.lagDays(), required - actual));
            }
        }
        return out;
    }

    // ── Cascade ───────────────────────────────────────────────────────────────

    /**
     * After editing one task's planned dates, pushes its descendants later where their dependencies
     * now require it. Only descendants of editedTaskId are considered; duration is preserved; nothing
     * is ever moved earlier. Completed or already-started descendants are never moved — they are
     * returned in blocked so the caller can tell the user.
     */
    public static Cascade cascade(List<Node> allNodes, List<Dep> allDeps,
                                  SimpleWorkingCalendar cal, UUID editedTaskId) {
        Model m = model(allNodes, allDeps);
        if (!m.byId().containsKey(editedTaskId)) return new Cascade(false, List.of(), List.of());

        List<UUID> order = topo(m);
        if (order.size() < m.byId().size()) return new Cascade(true, List.of(), List.of());

        Set<UUID> descendants = new HashSet<>();
        Deque<UUID> q = new ArrayDeque<>(List.of(editedTaskId));
        while (!q.isEmpty()) {
            for (Dep d : m.out().getOrDefault(q.poll(), List.of())) {
                if (descendants.add(d.successorId())) q.add(d.successorId());
            }
        }

        LocalDate earliest = m.byId().values().stream().map(Node::plannedStart).min(Comparator.naturalOrder()).orElseThrow();
        WorkDayIndex ix = new WorkDayIndex(cal, earliest);

        Map<UUID, int[]> pos = new HashMap<>();     // id → {startIdx, endIdx}
        for (Node n : m.byId().values()) {
            int s = ix.indexOf(n.plannedStart());
            pos.put(n.id(), new int[]{s, Math.max(s, ix.indexOfEnd(n.plannedEnd()))});
        }

        List<Shift> shifts = new ArrayList<>();
        List<UUID> blocked = new ArrayList<>();
        for (UUID id : order) {
            if (!descendants.contains(id)) continue;
            Node n = m.byId().get(id);
            int[] cur = pos.get(id);
            int dur = cur[1] - cur[0];                // end − start (inclusive length − 1)

            int required = Integer.MIN_VALUE;
            for (Dep d : m.in().getOrDefault(id, List.of())) {
                int[] p = pos.get(d.predecessorId());
                int need = switch (d.type()) {
                    case "SS" -> p[0] + d.lagDays();
                    case "FF" -> p[1] + d.lagDays() - dur;
                    case "SF" -> p[0] + d.lagDays() - dur;
                    default   -> p[1] + 1 + d.lagDays();           // FS
                };
                required = Math.max(required, need);
            }
            if (required <= cur[0]) continue;         // already fine — never pull earlier

            if (n.completed() || n.started()) { blocked.add(id); continue; }

            int oldStart = cur[0];
            cur[0] = required;
            cur[1] = required + dur;
            shifts.add(new Shift(id, n.plannedStart(), n.plannedEnd(),
                    ix.dateOf(cur[0]), ix.dateOf(cur[1]), required - oldStart));
        }
        return new Cascade(false, shifts, blocked);
    }

    // ── Graph plumbing ────────────────────────────────────────────────────────

    private record Model(Map<UUID, Node> byId, List<Dep> deps,
                         Map<UUID, List<Dep>> in, Map<UUID, List<Dep>> out) {}

    private static Model model(List<Node> all, List<Dep> allDeps) {
        Map<UUID, Node> byId = new LinkedHashMap<>();
        for (Node n : all) if (schedulable(n)) byId.put(n.id(), n);

        List<Dep> deps = allDeps.stream()
                .filter(d -> byId.containsKey(d.predecessorId()) && byId.containsKey(d.successorId())
                        && !d.predecessorId().equals(d.successorId()))
                .toList();
        Map<UUID, List<Dep>> in = new HashMap<>(), out = new HashMap<>();
        for (Dep d : deps) {
            in.computeIfAbsent(d.successorId(), k -> new ArrayList<>()).add(d);
            out.computeIfAbsent(d.predecessorId(), k -> new ArrayList<>()).add(d);
        }
        return new Model(byId, deps, in, out);
    }

    /** Kahn's algorithm. A result shorter than the node count means the graph has a cycle. */
    private static List<UUID> topo(Model m) {
        Map<UUID, Integer> indeg = new HashMap<>();
        for (UUID id : m.byId().keySet()) indeg.put(id, m.in().getOrDefault(id, List.of()).size());
        Deque<UUID> q = new ArrayDeque<>();
        for (UUID id : m.byId().keySet()) if (indeg.get(id) == 0) q.add(id);

        List<UUID> order = new ArrayList<>();
        while (!q.isEmpty()) {
            UUID id = q.poll();
            order.add(id);
            for (Dep d : m.out().getOrDefault(id, List.of())) {
                if (indeg.merge(d.successorId(), -1, Integer::sum) == 0) q.add(d.successorId());
            }
        }
        return order;
    }
}