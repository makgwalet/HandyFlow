package za.co.handyflow.platform.shared;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Notes which code took a database connection with no company and no system marker. While strict mode is off that works; once it is on those
 * calls see nothing. This list is what to fix (or wrap in TenantSession.system) before turning strict on.
 */
public final class UnscopedCallers {

    public record Caller(String where, long count) {}

    static final int MAX_CALLERS = 200;
    private static final String OURS = "za.co.handyflow.";
    /** The plumbing that stands between the caller and us. Compared by whole name, so a class that merely starts the same way is still a caller. */
    private static final java.util.Set<String> PLUMBING = java.util.Set.of(
            "za.co.handyflow.platform.shared.TenantSession", "za.co.handyflow.platform.shared.TenantSessionDataSource",
            "za.co.handyflow.platform.shared.TenantSessionConfig", "za.co.handyflow.platform.shared.UnscopedCallers");
    private static final Map<String, LongAdder> COUNTS = new ConcurrentHashMap<>();
    private static final Logger LOG = Logger.getLogger(UnscopedCallers.class.getName());

    private UnscopedCallers() {}

    /** The first of our own frames that is not this plumbing: that is who asked for the connection. */
    static String callerOf(StackTraceElement[] stack) {
        for (StackTraceElement e : stack) {
            String c = e.getClassName();
            if (c.startsWith(OURS) && !PLUMBING.contains(c)) {
                return c.substring(c.lastIndexOf('.') + 1) + "." + e.getMethodName();
            }
        }
        return "(no handyflow code on the stack)";
    }

    public static void note(StackTraceElement[] stack) {
        String where = callerOf(stack);
        LongAdder a = COUNTS.get(where);
        if (a == null) {
            if (COUNTS.size() >= MAX_CALLERS) return;
            a = COUNTS.computeIfAbsent(where, k -> new LongAdder());
            if (a.sum() == 0) LOG.warning("Database connection taken with no company: " + where + ". It will see nothing once row-level security is strict.");
        }
        a.increment();
    }

    public static List<Caller> top(int n) {
        List<Caller> all = new ArrayList<>();
        COUNTS.forEach((k, v) -> all.add(new Caller(k, v.sum())));
        all.sort(Comparator.comparingLong(Caller::count).reversed().thenComparing(Caller::where));
        return all.size() > n ? new ArrayList<>(all.subList(0, n)) : all;
    }

    static void reset() { COUNTS.clear(); }
}
