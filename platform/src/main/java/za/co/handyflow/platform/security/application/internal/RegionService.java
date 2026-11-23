package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regions: a level above branches. A branch is in at most one region; a user limited to a region sees every site of every branch in it
 * (see SiteScopeService). Nothing is copied: access follows the live membership. Every change is audited.
 *
 * Two changes widen or narrow other people's access and are worded that way in the audit: putting a branch into a region that users hold,
 * and taking one out. A region that a user holds cannot be deleted, so a limited user never silently becomes unrestricted.
 */
@Service
@RequiredArgsConstructor
public class RegionService {

    public record RegionView(UUID id, String name, String description, List<UUID> branchIds, int users) {}

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<RegionView> list(TenantId tenantId) {
        UUID t = tenantId.getValue();
        Map<UUID, List<UUID>> branches = new LinkedHashMap<>();
        jdbc.query("SELECT region_id, id FROM security_branches WHERE tenant_id = ? AND region_id IS NOT NULL ORDER BY name",
                (rs, i) -> branches.computeIfAbsent((UUID) rs.getObject("region_id"), k -> new ArrayList<>()).add((UUID) rs.getObject("id")), t);
        return jdbc.query("""
                SELECT r.id, r.name, r.description,
                       (SELECT COUNT(*) FROM security_user_regions x WHERE x.tenant_id = r.tenant_id AND x.region_id = r.id) AS users
                FROM security_regions r WHERE r.tenant_id = ? ORDER BY lower(r.name), r.id""",
                (rs, i) -> {
                    UUID id = (UUID) rs.getObject("id");
                    return new RegionView(id, rs.getString("name"), rs.getString("description"), branches.getOrDefault(id, List.of()), rs.getInt("users"));
                }, t);
    }

    @Transactional
    public List<RegionView> create(TenantId tenantId, UUID actor, String name, String description) {
        UUID t = tenantId.getValue();
        String n = RegionRules.cleanName(name);
        String d = RegionRules.cleanDescription(description);
        UUID id = UUID.randomUUID();
        int done = jdbc.update("INSERT INTO security_regions (id, tenant_id, name, description) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING", id, t, n, d);
        if (done == 0) throw duplicate();
        audit(t, actor, "REGION", id, "REGION_CREATED", n);
        return list(tenantId);
    }

    @Transactional
    public List<RegionView> update(TenantId tenantId, UUID actor, UUID id, String name, String description) {
        UUID t = tenantId.getValue();
        String n = RegionRules.cleanName(name);
        String d = RegionRules.cleanDescription(description);
        lock(t, id);
        Integer clash = jdbc.queryForObject("SELECT count(*) FROM security_regions WHERE tenant_id = ? AND lower(name) = lower(?) AND id <> ?", Integer.class, t, n, id);
        if (clash != null && clash > 0) throw duplicate();
        jdbc.update("UPDATE security_regions SET name = ?, description = ?, updated_at = now() WHERE id = ? AND tenant_id = ?", n, d, id, t);
        audit(t, actor, "REGION", id, "REGION_UPDATED", n);
        return list(tenantId);
    }

    /** Deleting is refused while any user is limited to the region. Its branches stay, with no region. */
    @Transactional
    public List<RegionView> delete(TenantId tenantId, UUID actor, UUID id) {
        UUID t = tenantId.getValue();
        lock(t, id);
        Integer holders = jdbc.queryForObject("SELECT count(*) FROM security_user_regions WHERE tenant_id = ? AND region_id = ?", Integer.class, t, id);
        if (holders != null && holders > 0) {
            throw new HandyFlowException(holders + (holders == 1 ? " user is" : " users are") + " limited to this region. Take it off them first, so nobody is left with more access than intended.",
                    HttpStatus.CONFLICT, "REGION_IN_USE");
        }
        jdbc.update("UPDATE security_branches SET region_id = NULL, region = NULL, updated_at = now() WHERE tenant_id = ? AND region_id = ?", t, id);
        jdbc.update("DELETE FROM security_regions WHERE id = ? AND tenant_id = ?", id, t);
        audit(t, actor, "REGION", id, "REGION_DELETED", null);
        return list(tenantId);
    }

    /**
     * Puts a branch in a region, or takes it out (regionId null). Users limited to the old region lose that branch's sites; users limited
     * to the new one gain them. The audit records how many users that touches.
     */
    @Transactional
    public List<RegionView> assignBranch(TenantId tenantId, UUID actor, UUID branchId, UUID regionId) {
        UUID t = tenantId.getValue();
        if (branchId == null) throw new HandyFlowException("A branch is needed.", HttpStatus.BAD_REQUEST, "BRANCH_REQUIRED");
        List<UUID> found = jdbc.queryForList("SELECT id FROM security_branches WHERE id = ? AND tenant_id = ? FOR UPDATE", UUID.class, branchId, t);
        if (found.isEmpty()) throw new HandyFlowException("Branch not found", HttpStatus.NOT_FOUND, "BRANCH_NOT_FOUND");
        if (regionId != null) lock(t, regionId);
        Integer touched = jdbc.queryForObject("""
                SELECT count(DISTINCT x.user_id) FROM security_user_regions x
                WHERE x.tenant_id = ? AND (x.region_id = ? OR x.region_id = (SELECT region_id FROM security_branches WHERE id = ? AND tenant_id = ?))""",
                Integer.class, t, regionId, branchId, t);
        if (regionId == null) {
            jdbc.update("UPDATE security_branches SET region_id = NULL, region = NULL, updated_at = now() WHERE id = ? AND tenant_id = ?", branchId, t);
        } else {
            jdbc.update("UPDATE security_branches SET region_id = ?, updated_at = now() WHERE id = ? AND tenant_id = ?", regionId, branchId, t);
        }
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, 'USER', 'BRANCH', ?, 'BRANCH_REGION_SET', jsonb_build_object('regionId', ?::text, 'usersAffected', ?::int), now())""",
                t, actor, branchId, regionId == null ? null : regionId.toString(), touched == null ? 0 : touched);
        return list(tenantId);
    }

    private void lock(UUID tenant, UUID regionId) {
        if (regionId == null) throw new HandyFlowException("A region is needed.", HttpStatus.BAD_REQUEST, "REGION_REQUIRED");
        List<UUID> r = jdbc.queryForList("SELECT id FROM security_regions WHERE id = ? AND tenant_id = ? FOR UPDATE", UUID.class, regionId, tenant);
        if (r.isEmpty()) throw new HandyFlowException("Region not found", HttpStatus.NOT_FOUND, "REGION_NOT_FOUND");
    }

    private static HandyFlowException duplicate() {
        return new HandyFlowException("A region with that name already exists.", HttpStatus.CONFLICT, "DUPLICATE_REGION_NAME");
    }

    private void audit(UUID tenant, UUID actor, String entityType, UUID entityId, String action, String name) {
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, 'USER', ?, ?, ?, jsonb_build_object('name', ?::text), now())""", tenant, actor, entityType, entityId, action, name);
    }
}
