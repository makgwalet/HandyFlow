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
    private static final ThreadLocal<String> USER_NAME = new ThreadLocal<>();

    public static void setTenantId(String tenantId) { TENANT_ID.set(tenantId); }
    public static void setUserId(String userId)      { USER_ID.set(userId); }
    public static void setUserName(String userName)  { USER_NAME.set(userName); }

    public static String getTenantId() { return TENANT_ID.get(); }
    public static String getUserId()   { return USER_ID.get(); }

    /** Returns true if a tenant is set — used by FeatureGuard in public contexts. */
    public static boolean hasTenant() { return TENANT_ID.get() != null; }

    public static TenantId getTenantIdAsObject() {
        String id = TENANT_ID.get();
        if (id == null) throw new IllegalStateException("No tenant in context");
        return TenantId.of(id);
    }

    public static UUID getCurrentUserId() {
        String id = USER_ID.get();
        if (id == null) throw new IllegalStateException("No user ID in context");
        return UUID.fromString(id);
    }

    /**
     * Returns the display name of the authenticated user.
     *
     * Reads from the USER_NAME ThreadLocal, which JwtAuthFilter must populate
     * alongside USER_ID. Falls back to USER_ID (still useful for logging) if
     * the name was not set.
     *
     * No SecurityContextHolder, no Jwt cast — consistent with how TENANT_ID
     * and USER_ID are already handled in this class.
     */
    public static String getCurrentUserName() {
        String name = USER_NAME.get();
        if (name != null && !name.isBlank()) return name;
        // Fallback: return the user ID so callers always get something non-null
        String id = USER_ID.get();
        return id != null ? id : "Unknown";
    }

    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
        USER_NAME.remove();
    }
}
