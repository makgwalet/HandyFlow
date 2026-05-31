package za.co.handyflow.platform.shared;

import java.util.UUID;

/**
 * Thread-local holder for the current tenant and user.
 * Populated by JwtAuthFilter on every authenticated request.
 *
 * WHY a static thread-local?
 * Spring's request processing is single-threaded per request.
 * This gives every class in the call stack access to the current
 * tenant and user without passing them as method parameters everywhere.
 */
public class TenantContext {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID   = new ThreadLocal<>();

    public static void setTenantId(String tenantId) { TENANT_ID.set(tenantId); }
    public static void setUserId(String userId)      { USER_ID.set(userId); }

    public static String getTenantId() { return TENANT_ID.get(); }
    public static String getUserId()   { return USER_ID.get(); }

    /** Returns true if a tenant is set — used by FeatureGuard in public contexts. */
    public static boolean hasTenant() { return TENANT_ID.get() != null; }

    public static TenantId getTenantIdAsObject() {
        String id = TENANT_ID.get();
        if (id == null) throw new IllegalStateException("No tenant in context");
        return TenantId.of(id);   // use the static factory, not the private constructor
    }

    /**
     * Returns the UUID of the currently authenticated user.
     * Used by controllers that need to know WHO is performing an action.
     */
    public static UUID getCurrentUserId() {
        String id = USER_ID.get();
        if (id == null) throw new IllegalStateException("No user ID in context");
        return UUID.fromString(id);
    }

    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
    }
}
