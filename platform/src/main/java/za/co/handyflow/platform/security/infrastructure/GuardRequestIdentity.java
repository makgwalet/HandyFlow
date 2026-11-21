package za.co.handyflow.platform.security.infrastructure;

import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Puts an authenticated guard into TenantContext for the length of one request, so the guard controllers can call TenantContext.getTenantIdAsObject() and
 * getCurrentUserId() like every other controller. (TenantContext is a plain thread-local; it does not read the Spring SecurityContext.)
 *
 * The ids are parsed here, so a token whose tenant or guard id is not a UUID is refused at the door instead of failing later in a controller.
 */
final class GuardRequestIdentity {

    private GuardRequestIdentity() {}

    /** @throws IllegalArgumentException when the tenant or guard id is missing or not a UUID; nothing is set in that case. */
    static void enter(String tenantId, String guardId, String firstName, String lastName) {
        UUID tenant = parse(tenantId, "tenant");
        UUID guard = parse(guardId, "guard");
        TenantContext.setTenantId(tenant.toString());
        TenantContext.setUserId(guard.toString());
        TenantContext.setUserName(displayName(firstName, lastName, guard));
        TenantContext.setImpersonation(false);
    }

    /** Always call in a finally: the thread goes back to the pool. */
    static void leave() {
        TenantContext.clear();
    }

    static String displayName(String first, String last, UUID fallback) {
        String full = ((first == null ? "" : first.trim()) + " " + (last == null ? "" : last.trim())).trim();
        return full.isEmpty() ? fallback.toString() : full;
    }

    private static UUID parse(String value, String what) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Token has no " + what + " id");
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Token " + what + " id is not valid");
        }
    }
}
