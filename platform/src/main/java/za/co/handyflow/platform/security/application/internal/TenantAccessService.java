package za.co.handyflow.platform.security.application.internal;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.billing.application.SubscriptionQueryFacade;
import za.co.handyflow.platform.shared.TenantId;

/** Looks up a tenant's standing for the guard app, cached briefly so a patrol scan does not cost a billing query. */
@Slf4j
@Service
public class TenantAccessService {

    private static final long TTL_MS = 60_000L;

    private record Cached(TenantAccessRules.Access access, long at) {}

    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private SubscriptionQueryFacade subscriptions;

    public TenantAccessRules.Access accessFor(UUID tenantId) {
        if (tenantId == null || subscriptions == null) return TenantAccessRules.Access.FULL;
        long now = System.currentTimeMillis();
        Cached c = cache.get(tenantId);
        if (c != null && now - c.at() < TTL_MS) return c.access();
        TenantAccessRules.Access access;
        try {
            access = TenantAccessRules.accessFor(subscriptions.getSubscription(TenantId.of(tenantId)).status());
        } catch (Exception e) {
            // Never lock guards out because billing could not be read; keep the last known answer if there is one.
            log.warn("Could not read subscription for tenant={}: {}", tenantId, e.getMessage());
            return c != null ? c.access() : TenantAccessRules.Access.FULL;
        }
        cache.put(tenantId, new Cached(access, now));
        return access;
    }
}
