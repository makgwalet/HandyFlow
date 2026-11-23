package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Administration of who is limited to which sites: the overview of everyone who is limited, and the branch part of a user's access
 * (the client and site part lives in SiteScopeService). Nothing is deleted: a branch that is taken away is switched off and stays on record.
 */
@Service
@RequiredArgsConstructor
public class SiteAccessAdminService {

    public record LimitedUser(UUID userId, String name, String email, String status, int branches, int clients, int sites, int visibleSites, int regions) {
        public LimitedUser(UUID userId, String name, String email, String status, int branches, int clients, int sites, int visibleSites) {
            this(userId, name, email, status, branches, clients, sites, visibleSites, 0);
        }
    }

    public record BranchEntry(UUID branchId, String role) {}

    static final String LIMITED_USERS = """
            SELECT u.id, u.first_name, u.last_name, u.email, u.status,
                   (SELECT COUNT(*) FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER' AND a.entity_id = u.id
                        AND a.active AND a.role IN ('MANAGER', 'VIEWER')) AS branches,
                   (SELECT COUNT(*) FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id) AS regions,
                   (SELECT COUNT(*) FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'CLIENT') AS clients,
                   (SELECT COUNT(*) FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'SITE') AS sites,
                   (SELECT COUNT(*) FROM security_sites s WHERE s.tenant_id = u.tenant_id AND s.deleted_at IS NULL AND (
                        s.branch_id IN (SELECT a.branch_id FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER'
                                        AND a.entity_id = u.id AND a.active AND a.role IN ('MANAGER', 'VIEWER'))
                     OR s.branch_id IN (SELECT b.id FROM security_branches b WHERE b.tenant_id = u.tenant_id AND b.region_id IN
                                        (SELECT g.region_id FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id))
                     OR s.id IN (SELECT x.scope_id FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'SITE')
                     OR s.customer_id IN (SELECT x.scope_id FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id AND x.scope_type = 'CLIENT'))) AS visible_sites
            FROM users u
            WHERE u.tenant_id = ? AND (
                  EXISTS (SELECT 1 FROM security_branch_assignments a WHERE a.tenant_id = u.tenant_id AND a.entity_type = 'USER' AND a.entity_id = u.id
                          AND a.active AND a.role IN ('MANAGER', 'VIEWER'))
               OR EXISTS (SELECT 1 FROM security_user_scopes x WHERE x.tenant_id = u.tenant_id AND x.user_id = u.id)
               OR EXISTS (SELECT 1 FROM security_user_regions g WHERE g.tenant_id = u.tenant_id AND g.user_id = u.id))
            ORDER BY u.last_name, u.first_name, u.id""";

    private final JdbcTemplate jdbc;
    private final SiteScopeService scope;

    /** Everyone whose access is limited, with how they are limited and how many sites that leaves them. A user who can see 0 sites sees nothing. */
    @Transactional(readOnly = true)
    public List<LimitedUser> limitedUsers(TenantId tenantId) {
        return jdbc.query(LIMITED_USERS, (rs, i) -> new LimitedUser(rs.getObject("id", UUID.class),
                (rs.getString("first_name") + " " + rs.getString("last_name")).trim(), rs.getString("email"), rs.getString("status"),
                rs.getInt("branches"), rs.getInt("clients"), rs.getInt("sites"), rs.getInt("visible_sites"), rs.getInt("regions")), tenantId.getValue());
    }

    /**
     * Replaces the branches a user is limited to. The listed branches are switched on with their role; the user's other MANAGER or VIEWER
     * branches are switched off. An empty list removes the branch part. Guard assignments are never touched.
     */
    @Transactional
    public SiteScopeService.View replaceBranches(TenantId tenantId, UUID actor, UUID userId, List<BranchEntry> asked) {
        UUID t = tenantId.getValue();
        SiteScopeRules.requireNotSelf(actor, userId);
        List<BranchAccessRules.Entry> entries = BranchAccessRules.clean(asked == null ? null
                : asked.stream().map(e -> e == null ? null : new BranchAccessRules.Entry(e.branchId(), e.role())).toList());

        // lock the user row so two administrators editing the same person queue up instead of interleaving
        List<UUID> user = jdbc.queryForList("SELECT id FROM users WHERE id = ? AND tenant_id = ? FOR UPDATE", UUID.class, userId, t);
        if (user.isEmpty()) throw new HandyFlowException("User not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        for (BranchAccessRules.Entry e : entries) {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM security_branches WHERE id = ? AND tenant_id = ? AND active", Integer.class, e.branchId(), t);
            if (n == null || n == 0) throw new HandyFlowException("A chosen branch was not found or is switched off.", HttpStatus.BAD_REQUEST, "BRANCH_UNKNOWN");
        }

        List<Object> off = new ArrayList<>(List.of(t, userId));
        entries.forEach(e -> off.add(e.branchId()));
        jdbc.update("UPDATE security_branch_assignments SET active = false WHERE tenant_id = ? AND entity_type = 'USER' AND entity_id = ? AND active "
                + "AND role IN ('MANAGER', 'VIEWER')" + BranchAccessRules.keepFilter(entries.size()), off.toArray());
        for (BranchAccessRules.Entry e : entries) {
            jdbc.update("""
                    INSERT INTO security_branch_assignments (tenant_id, branch_id, entity_type, entity_id, role, assigned_by, active)
                    VALUES (?, ?, 'USER', ?, ?, ?, true)
                    ON CONFLICT (branch_id, entity_type, entity_id) DO UPDATE SET role = EXCLUDED.role, active = true, assigned_at = now(), assigned_by = EXCLUDED.assigned_by""",
                    t, e.branchId(), userId, e.role(), actor);
        }
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, 'USER', 'USER', ?, 'BRANCH_ACCESS_SET', ?::jsonb, now())""", t, actor, userId, audit(entries));
        return scope.view(tenantId, userId);
    }

    /**
     * Replaces the regions a user is limited to. Each region brings in every branch it holds, now and later. An empty list removes the region part.
     * Removing the last region of a user who has nothing else leaves them with the whole company, exactly like removing the last branch.
     */
    @Transactional
    public SiteScopeService.View replaceRegions(TenantId tenantId, UUID actor, UUID userId, List<UUID> asked) {
        UUID t = tenantId.getValue();
        SiteScopeRules.requireNotSelf(actor, userId);
        List<UUID> ids = SiteScopeRules.clean(asked, "regions");

        List<UUID> user = jdbc.queryForList("SELECT id FROM users WHERE id = ? AND tenant_id = ? FOR UPDATE", UUID.class, userId, t);
        if (user.isEmpty()) throw new HandyFlowException("User not found", HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        for (UUID id : ids) {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM security_regions WHERE id = ? AND tenant_id = ?", Integer.class, id, t);
            if (n == null || n == 0) throw new HandyFlowException("A chosen region was not found.", HttpStatus.BAD_REQUEST, "REGION_UNKNOWN");
        }
        jdbc.update("DELETE FROM security_user_regions WHERE tenant_id = ? AND user_id = ?", t, userId);
        for (UUID id : ids) {
            jdbc.update("INSERT INTO security_user_regions (tenant_id, user_id, region_id, created_by) VALUES (?, ?, ?, ?)", t, userId, id, actor);
        }
        StringBuilder meta = new StringBuilder("{\"regions\":").append(ids.size()).append(",\"regionIds\":[");
        for (int i = 0; i < ids.size(); i++) meta.append(i > 0 ? "," : "").append('"').append(ids.get(i)).append('"');
        meta.append("]}");
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, 'USER', 'USER', ?, 'REGION_ACCESS_SET', ?::jsonb, now())""", t, actor, userId, meta.toString());
        return scope.view(tenantId, userId);
    }

    private static String audit(List<BranchAccessRules.Entry> entries) {
        StringBuilder b = new StringBuilder("{\"branches\":").append(entries.size()).append(",\"entries\":[");
        for (int i = 0; i < entries.size(); i++) {
            b.append(i > 0 ? "," : "").append("{\"branchId\":\"").append(entries.get(i).branchId()).append("\",\"role\":\"").append(entries.get(i).role()).append("\"}");
        }
        return b.append("]}").toString();
    }
}
