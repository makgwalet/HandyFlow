package za.co.handyflow.platform.identity.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.billing.application.BillingFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entitlement on the token (IAM Phase 1, item 2): the permissions put into a login, refresh or invitation token (and
 * returned to the browser) leave out those that belong to a module the tenant does not hold. A permission can then never
 * grant access to a module the tenant has not got, even if a controller forgot its module check.
 * <p>
 * The owning module of each permission is {@code permissions.module_key} (V590). Never dropped: {@code core} permissions,
 * {@code unassigned} ones, and permissions of modules that have no {@code module_catalogue} row (projects, ap,
 * accountant and similar, which stay report-only exactly as in ModuleEntitlementPaths).
 * <p>
 * Mode: {@code handyflow.entitlement.token-enforce} (env ENTITLEMENT_TOKEN_ENFORCE), default false. Off = report only:
 * what would be dropped is logged once per tenant and module ("TOKEN-ENTITLEMENT-REPORT") and the permissions are returned
 * unchanged. Trial, internal and demo tenants may use a module without a subscription row, so read the log before
 * switching it on (docs/commercial/06_ENFORCEMENT_RUNBOOK.md).
 * <p>
 * Login must never fail because of this class: any error returns the permissions unchanged.
 */
@Slf4j
@Component
public class TokenPermissions {

    private final JdbcTemplate jdbc;
    private final BillingFacade billing;
    private final boolean enforce;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public TokenPermissions(JdbcTemplate jdbc, BillingFacade billing,
                            @Value("${handyflow.entitlement.token-enforce:false}") boolean enforce) {
        this.jdbc = jdbc;
        this.billing = billing;
        this.enforce = enforce;
    }

    /** The permissions to put in {@code tenantId}'s token: {@code all} minus those of modules the tenant does not hold. */
    public Set<String> forToken(UUID tenantId, Set<String> all) {
        if (all == null || all.isEmpty() || tenantId == null) return all;
        try {
            Map<String, String> moduleOf = new HashMap<>();
            jdbc.query("SELECT name, module_key FROM permissions WHERE name = ANY (?)",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> { moduleOf.put(rs.getString(1), rs.getString(2)); },
                    (Object) all.toArray(new String[0]));
            Set<String> catalogued = new HashSet<>(jdbc.queryForList("SELECT key FROM module_catalogue", String.class));

            TenantId tenant = TenantId.of(tenantId);
            Map<String, Boolean> held = new HashMap<>();
            Map<String, Integer> droppedPerModule = new HashMap<>();
            Set<String> dropped = toDrop(all, moduleOf, catalogued,
                    module -> held.computeIfAbsent(module, m -> billing.isModuleActive(tenant, m)), droppedPerModule);
            if (dropped.isEmpty()) return all;

            for (var e : droppedPerModule.entrySet()) {
                if (reported.add(tenantId + "|" + e.getKey())) {
                    log.warn("TOKEN-ENTITLEMENT-REPORT tenant={} module={} {} permission(s) {} (enforcement is {})",
                            tenantId, e.getKey(), e.getValue(), enforce ? "removed from the token" : "would be removed from the token",
                            enforce ? "on" : "off");
                }
            }
            if (!enforce) return all;
            Set<String> kept = new HashSet<>(all);
            kept.removeAll(dropped);
            return kept;
        } catch (RuntimeException e) {
            log.warn("Could not apply module entitlement to the token permissions for tenant={}: {}", tenantId, e.getMessage());
            return all;
        }
    }

    /**
     * The permissions to drop, and (in {@code perModule}) how many per module. Pure so it is tested without Spring.
     * A permission is dropped only when its module has a catalogue row and the tenant does not hold that module.
     */
    static Set<String> toDrop(Set<String> all, Map<String, String> moduleOf, Set<String> catalogued,
                              java.util.function.Predicate<String> tenantHolds, Map<String, Integer> perModule) {
        Set<String> dropped = new HashSet<>();
        for (String permission : all) {
            String module = moduleOf.get(permission);
            if (module == null || "core".equals(module) || "unassigned".equals(module) || !catalogued.contains(module)) continue;
            if (!tenantHolds.test(module)) { dropped.add(permission); perModule.merge(module, 1, Integer::sum); }
        }
        return dropped;
    }
}
