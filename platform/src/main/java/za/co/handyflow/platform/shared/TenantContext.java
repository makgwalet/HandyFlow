package za.co.handyflow.platform.shared;

import java.util.UUID;

/**
 * WHY ThreadLocal?
 *
 * Every HTTP request runs on its own thread.
 * ThreadLocal stores data PER THREAD — so each request
 * has its own isolated tenantId without interfering with others.
 *
 * Flow:
 * Request arrives → JWT filter extracts tenantId
 *                 → stores in TenantContext
 *                 → controller/service calls TenantContext.get()
 *                 → response sent → TenantContext.clear() called
 *
 * WHY clear() is critical:
 * Thread pools REUSE threads. If you don't clear, the next request
 * on the same thread inherits the previous request's tenantId.
 * That's a catastrophic data leak between tenants.
 */
public final class TenantContext {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static TenantId getTenantIdAsObject() {
        String id = TENANT_ID.get();
        if (id == null) {
            throw new IllegalStateException(
                    "No tenant in context — request missing JWT or JWT filter not configured"
            );
        }
        // WHY UUID.fromString(id)?
        // TenantContext stores tenantId as a String (from JWT claims).
        // TenantId value object wraps a UUID.
        // We convert String → UUID → TenantId here.
        return TenantId.of(UUID.fromString(id));
    }

    public static boolean hasTenant() {
        return TENANT_ID.get() != null;
    }

    public static void clear() {
        TENANT_ID.remove(); // WHY remove() not set(null)?
        // remove() fully detaches the value from the thread.
        // set(null) leaves the ThreadLocal entry in memory — a subtle leak.
    }
}
