package za.co.handyflow.platform.legalpractice.application.internal;

import za.co.handyflow.platform.legalpractice.domain.model.LpMatter;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * What the matter-facing services ask before they show or change anything on a matter. The person asked about is the signed-in
 * user. Refusal is an {@code AccessDeniedException} (403) and is recorded.
 */
public interface LpWallGuard {

    /** Throws if a wall bars the signed-in user from this matter (directly or through its client). */
    void requireAccess(TenantId tenantId, LpMatter matter);

    /** As {@link #requireAccess}, from a matter id. A matter that does not exist passes: the caller reports not-found as it always did. */
    void requireMatterAccess(TenantId tenantId, UUID matterId);

    /**
     * Throws if a wall bars the signed-in user from this matter or this client. The matter may be null (a client-level record such as a
     * retainer-only invoice): then only a wall on the client applies.
     */
    void requireScopeAccess(TenantId tenantId, UUID matterId, UUID clientId);

    /** Throws if a wall bars the signed-in user from this client. */
    void requireClientAccess(TenantId tenantId, UUID clientId);

    /** The matters and clients the signed-in user is barred from, for list queries. Never empty lists: a placeholder id stands in. */
    Barred barred(TenantId tenantId);

    record Barred(List<UUID> matterIds, List<UUID> clientIds) {
        public static final UUID NONE_ID = new UUID(0L, 0L);

        public static Barred of(List<UUID> matters, List<UUID> clients) {
            return new Barred(matters.isEmpty() ? List.of(NONE_ID) : matters, clients.isEmpty() ? List.of(NONE_ID) : clients);
        }
    }

    /** No walls at all. For tests of other services. */
    LpWallGuard NONE = new LpWallGuard() {
        @Override public void requireAccess(TenantId t, LpMatter m) { }
        @Override public void requireMatterAccess(TenantId t, UUID id) { }
        @Override public void requireClientAccess(TenantId t, UUID id) { }
        @Override public void requireScopeAccess(TenantId t, UUID m, UUID c) { }
        @Override public Barred barred(TenantId t) { return Barred.of(List.of(), List.of()); }
    };
}
