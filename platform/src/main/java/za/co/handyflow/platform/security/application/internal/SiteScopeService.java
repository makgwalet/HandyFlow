package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which sites a staff user may see. No scope at all means the whole company (as before). Any scope (branch assignment, client or site)
 * limits the user to the sites in it. Guards are not staff users: their endpoints take the site from their open session.
 */
@Service
@RequiredArgsConstructor
public class SiteScopeService {

    public record Named(UUID id, String name) {}
    public record BranchScope(UUID id, String name, String role) {}
    /** `regions` are the regions the user is limited to (each brings in every branch it holds, now and later). */
    public record View(boolean restricted, List<BranchScope> branches, List<Named> clients, List<Named> sites, int visibleSites, List<Named> regions) {
        public View(boolean restricted, List<BranchScope> branches, List<Named> clients, List<Named> sites, int visibleSites) {
            this(restricted, branches, clients, sites, visibleSites, List.of());
        }
    }

    private final JdbcTemplate jdbc;

    static final String HAS_SCOPE = """
            SELECT (EXISTS (SELECT 1 FROM security_branch_assignments WHERE tenant_id = ? AND entity_type = 'USER' AND entity_id = ? AND active
                            AND role IN ('MANAGER', 'VIEWER'))
                 OR EXISTS (SELECT 1 FROM security_user_scopes WHERE tenant_id = ? AND user_id = ?)
                 OR EXISTS (SELECT 1 FROM security_user_regions WHERE tenant_id = ? AND user_id = ?))""";

    static final String ALLOWED_SITES = """
            SELECT s.id FROM security_sites s
            WHERE s.tenant_id = ? AND s.deleted_at IS NULL AND (
                  s.branch_id IN (SELECT branch_id FROM security_branch_assignments
                                  WHERE tenant_id = ? AND entity_type = 'USER' AND entity_id = ? AND active AND role IN ('MANAGER', 'VIEWER'))
               OR s.id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = ? AND user_id = ? AND scope_type = 'SITE')
               OR s.customer_id IN (SELECT scope_id FROM security_user_scopes WHERE tenant_id = ? AND user_id = ? AND scope_type = 'CLIENT')
               OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = ? AND b.region_id IN
                                  (SELECT r.region_id FROM security_user_regions r WHERE r.tenant_id = b.tenant_id AND r.user_id = ?)))""";

    /** The sites the user may see, or null when the user may see the whole company. */
    @Transactional(readOnly = true)
    public Set<UUID> allowedSites(TenantId tenantId, UUID userId) {
        if (userId == null) return null;
        UUID t = tenantId.getValue();
        Boolean scoped = jdbc.queryForObject(HAS_SCOPE, Boolean.class, t, userId, t, userId, t, userId);
        if (scoped == null || !scoped) return null;
        return new HashSet<>(jdbc.queryForList(ALLOWED_SITES, UUID.class, t, t, userId, t, userId, t, userId, t, userId));
    }

    public static Set<UUID> allowedSites(SiteScopeService s, TenantId tenantId, UUID userId) {
        return s == null ? null : s.allowedSites(tenantId, userId);
    }

    /** Refuses a site outside the user's scope as "not found". Null-safe so a hand-built controller still works. */
    public static void check(SiteScopeService s, TenantId tenantId, UUID userId, UUID siteId) {
        if (s == null) return;
        SiteScopeRules.requireSite(s.allowedSites(tenantId, userId), siteId);
    }

    /**
     * The records that belong to a site, and the fixed query that finds the site of one. The text is here, never taken from a request.
     * Every query is tenant-scoped on the record itself.
     */
    public enum Kind {
        POST("security_posts"), POST_ORDER("security_post_orders"), CONTACT("security_contacts"), CAMERA("security_cameras"),
        ALARM_EVENT("security_alarm_events"), PATROL_ROUTE("security_patrol_routes"), CHECKPOINT("security_checkpoints"),
        ACCESS_POINT("security_access_points"), GATE_ENTRY("security_gate_register_entries"), INCIDENT("security_incidents"),
        SHIFT("security_shifts"), PATROL_ROUND("security_patrol_rounds"), ROTATION_PATTERN("security_rotation_patterns"),
        /** An assignment belongs to the site of its rotation pattern. */
        ROTATION_ASSIGNMENT("SELECT p.site_id FROM security_rotation_assignments a JOIN security_rotation_patterns p ON p.id = a.pattern_id AND p.tenant_id = a.tenant_id "
                + "WHERE a.id = ? AND a.tenant_id = ?"),
        /** A swap request belongs to the site of the shift being swapped. */
        SWAP("SELECT s.site_id FROM security_shift_swap_requests r JOIN security_shifts s ON s.id = r.original_shift_id AND s.tenant_id = r.tenant_id "
                + "WHERE r.id = ? AND r.tenant_id = ?"),
        /** A dispatch belongs to the site of the alarm it answers. */
        DISPATCH("SELECT e.site_id FROM security_dispatches d JOIN security_alarm_events e ON e.id = d.alarm_event_id AND e.tenant_id = d.tenant_id "
                + "WHERE d.id = ? AND d.tenant_id = ?");

        final String sql;
        Kind(String tableOrSql) {
            this.sql = tableOrSql.startsWith("SELECT ") ? tableOrSql : "SELECT site_id FROM " + tableOrSql + " WHERE id = ? AND tenant_id = ?";
        }
    }

    /** Refuses a record at a site outside the user's scope as "not found". Costs nothing extra for a user with the whole company. */
    public static void checkChild(SiteScopeService s, TenantId tenantId, UUID userId, Kind kind, UUID id) {
        if (s == null) return;
        Set<UUID> allowed = s.allowedSites(tenantId, userId);
        if (allowed == null) return;
        List<UUID> rows = s.jdbc.query(kind.sql,
                (rs, i) -> rs.getObject(1, UUID.class), id, tenantId.getValue());
        SiteScopeRules.requireChild(allowed, !rows.isEmpty(), rows.isEmpty() ? null : rows.get(0));
    }

    /** A record whose site is optional in the request (a tenant-wide contact, an alarm with no site): no site means the whole company. */
    public static void checkOptionalSite(SiteScopeService s, TenantId tenantId, UUID userId, UUID siteId) {
        if (s == null) return;
        if (siteId == null) SiteScopeRules.requireUnrestricted(s.allowedSites(tenantId, userId));
        else SiteScopeRules.requireSite(s.allowedSites(tenantId, userId), siteId);
    }

    /** Optional-site lists: see {@link SiteScopeRules#requireChoice}. */
    public static void checkChoice(SiteScopeService s, TenantId tenantId, UUID userId, UUID siteId) {
        if (s == null) return;
        SiteScopeRules.requireChoice(s.allowedSites(tenantId, userId), siteId);
    }

    public static void checkUnrestricted(SiteScopeService s, TenantId tenantId, UUID userId) {
        if (s == null) return;
        SiteScopeRules.requireUnrestricted(s.allowedSites(tenantId, userId));
    }

    @Transactional(readOnly = true)
    public View view(TenantId tenantId, UUID userId) {
        UUID t = tenantId.getValue();
        List<BranchScope> branches = jdbc.query("""
                SELECT b.id, b.name, a.role FROM security_branch_assignments a JOIN security_branches b ON b.id = a.branch_id
                WHERE a.tenant_id = ? AND a.entity_type = 'USER' AND a.entity_id = ? AND a.active AND a.role IN ('MANAGER', 'VIEWER') ORDER BY b.name""",
                (rs, i) -> new BranchScope((UUID) rs.getObject("id"), rs.getString("name"), rs.getString("role")), t, userId);
        List<Named> sites = jdbc.query("""
                SELECT s.id, s.name FROM security_user_scopes u JOIN security_sites s ON s.id = u.scope_id AND s.tenant_id = u.tenant_id
                WHERE u.tenant_id = ? AND u.user_id = ? AND u.scope_type = 'SITE' AND s.deleted_at IS NULL ORDER BY s.name""",
                (rs, i) -> new Named((UUID) rs.getObject("id"), rs.getString("name")), t, userId);
        List<Named> clients = jdbc.query("""
                SELECT u.scope_id AS id, COALESCE(c.name, 'Client') AS name
                FROM security_user_scopes u LEFT JOIN customers c ON c.id = u.scope_id AND c.tenant_id = u.tenant_id
                WHERE u.tenant_id = ? AND u.user_id = ? AND u.scope_type = 'CLIENT' ORDER BY 2""",
                (rs, i) -> new Named((UUID) rs.getObject("id"), rs.getString("name")), t, userId);
        List<Named> regions = jdbc.query("""
                SELECT r.id, r.name FROM security_user_regions x JOIN security_regions r ON r.id = x.region_id AND r.tenant_id = x.tenant_id
                WHERE x.tenant_id = ? AND x.user_id = ? ORDER BY r.name""",
                (rs, i) -> new Named((UUID) rs.getObject("id"), rs.getString("name")), t, userId);
        Set<UUID> allowed = allowedSites(tenantId, userId);
        return new View(allowed != null, branches, clients, sites, allowed == null ? -1 : allowed.size(), regions);
    }

    /** Replaces the user's client and site scope (the branch part is managed with the branch assignments). An empty set of both removes this part. */
    @Transactional
    public View replace(TenantId tenantId, UUID actor, UUID userId, List<UUID> clientIds, List<UUID> siteIds) {
        UUID t = tenantId.getValue();
        SiteScopeRules.requireNotSelf(actor, userId);
        List<UUID> clients = SiteScopeRules.clean(clientIds, "clients");
        List<UUID> sites = SiteScopeRules.clean(siteIds, "sites");

        Integer user = jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ? AND tenant_id = ?", Integer.class, userId, t);
        if (user == null || user == 0) throw new HandyFlowException("User not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        for (UUID s : sites) {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM security_sites WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", Integer.class, s, t);
            if (n == null || n == 0) throw new HandyFlowException("A chosen site was not found.", HttpStatus.BAD_REQUEST, "SCOPE_SITE_UNKNOWN");
        }
        for (UUID c : clients) {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM customers WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", Integer.class, c, t);
            if (n == null || n == 0) throw new HandyFlowException("A chosen client was not found.", HttpStatus.BAD_REQUEST, "SCOPE_CLIENT_UNKNOWN");
        }

        jdbc.update("DELETE FROM security_user_scopes WHERE tenant_id = ? AND user_id = ?", t, userId);
        for (UUID s : sites) insert(t, userId, "SITE", s, actor);
        for (UUID c : clients) insert(t, userId, "CLIENT", c, actor);
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, 'USER', 'USER', ?, 'SITE_SCOPE_SET', ?::jsonb, now())""",
                t, actor, userId, "{\"clients\":" + clients.size() + ",\"sites\":" + sites.size() + ",\"siteIds\":" + json(sites) + ",\"clientIds\":" + json(clients) + "}");
        return view(tenantId, userId);
    }

    private void insert(UUID tenant, UUID user, String type, UUID scopeId, UUID actor) {
        jdbc.update("INSERT INTO security_user_scopes (tenant_id, user_id, scope_type, scope_id, created_by) VALUES (?, ?, ?, ?, ?)", tenant, user, type, scopeId, actor);
    }

    private static String json(List<UUID> ids) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) b.append(i > 0 ? "," : "").append('"').append(ids.get(i)).append('"');
        return b.append(']').toString();
    }
}
