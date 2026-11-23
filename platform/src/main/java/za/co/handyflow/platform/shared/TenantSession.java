package za.co.handyflow.platform.shared;

import java.util.function.Supplier;

/**
 * What a database connection should be told about who it is working for, so row-level security (V866) can refuse other companies' rows.
 * Normally that is the company of the signed-in user. A job that really must look across companies runs inside {@link #system}.
 * <p>
 * The connection is told when it is taken from the pool, so wrap the whole call, including any @Transactional method, not just a part of it.
 */
public final class TenantSession {

    /** What to tell the connection: the company (empty if none is known) and whether this is a deliberate cross-company job. */
    public record State(String tenantId, boolean bypass) {}

    private static final ThreadLocal<Boolean> SYSTEM = new ThreadLocal<>();

    private TenantSession() {}

    public static boolean isSystem() { return Boolean.TRUE.equals(SYSTEM.get()); }

    public static State current() {
        String t = TenantContext.getTenantId();
        return new State(t == null ? "" : t.trim(), isSystem());
    }

    public static <T> T system(Supplier<T> work) {
        Boolean before = SYSTEM.get();
        SYSTEM.set(Boolean.TRUE);
        try {
            return work.get();
        } finally {
            if (before == null) SYSTEM.remove(); else SYSTEM.set(before);
        }
    }

    public static void system(Runnable work) {
        system(() -> { work.run(); return null; });
    }
}
