package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The warehouses of a tenant. A location belongs to one of them; the first one (MAIN) is made when the tenant first needs it. */
@Service
@RequiredArgsConstructor
public class WhseWarehouseService {

    private final JdbcTemplate jdbc;

    /** Optional so the services can be built without it; Spring injects it. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private WhsePackageService packages;

    private void requireRoom(TenantId tenantId, WhsePackageRules.Limit limit) {
        if (packages != null) packages.requireRoomFor(tenantId, limit);
    }

    public record Warehouse(UUID id, String code, String name, String address, boolean active, boolean isDefault, int locations, BigDecimal stockOnHand, Instant createdAt) {}

    private static final String SELECT = """
            SELECT w.id, w.code, w.name, w.address, w.active, w.is_default, w.created_at,
                   (SELECT count(*) FROM whse_locations l WHERE l.warehouse_id = w.id AND l.deleted_at IS NULL),
                   (SELECT coalesce(sum(i.qty_on_hand), 0) FROM whse_inventory i JOIN whse_locations l ON l.id = i.location_id WHERE l.warehouse_id = w.id)
              FROM whse_warehouses w WHERE w.tenant_id = ? AND w.deleted_at IS NULL""";

    private static Warehouse row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Warehouse(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getBoolean(6), rs.getInt(8), rs.getBigDecimal(9), rs.getTimestamp(7).toInstant());
    }

    @Transactional(readOnly = true)
    public List<Warehouse> list(TenantId tenantId) {
        return jdbc.query(SELECT + " ORDER BY w.is_default DESC, w.code", WhseWarehouseService::row, tenantId.getValue());
    }

    @Transactional(readOnly = true)
    public Warehouse get(TenantId tenantId, UUID id) {
        List<Warehouse> r = jdbc.query(SELECT + " AND w.id = ?", WhseWarehouseService::row, tenantId.getValue(), id);
        if (r.isEmpty()) throw new ResourceNotFoundException("WhseWarehouse", id.toString());
        return r.get(0);
    }

    /**
     * The warehouse to use: the one asked for (it must exist, be this tenant's and be switched on), else the default. Null when the tenant has none yet,
     * which means "no warehouse filter" for the older code paths; the database makes the first one when the first location is created.
     */
    @Transactional(readOnly = true)
    public UUID resolve(TenantId tenantId, UUID requested) {
        if (requested != null) {
            List<Boolean> active = jdbc.query("SELECT active FROM whse_warehouses WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL", (rs, i) -> rs.getBoolean(1), tenantId.getValue(), requested);
            if (active == null || active.isEmpty()) throw new ResourceNotFoundException("WhseWarehouse", requested.toString());
            if (!active.get(0)) throw new IllegalStateException("That warehouse is switched off.");
            return requested;
        }
        List<UUID> d = jdbc.query("SELECT id FROM whse_warehouses WHERE tenant_id = ? AND is_default AND deleted_at IS NULL", (rs, i) -> rs.getObject(1, UUID.class), tenantId.getValue());
        return d == null || d.isEmpty() ? null : d.get(0);
    }

    @Transactional
    public Warehouse create(TenantId tenantId, String code, String name, String address, UUID actor) {
        requireActor(actor);
        String c = WhseWarehouseRules.code(code);
        requireRoom(tenantId, WhsePackageRules.Limit.WAREHOUSES);
        String n = WhseWarehouseRules.name(name);
        Integer clash = jdbc.queryForObject("SELECT count(*) FROM whse_warehouses WHERE tenant_id = ? AND code = ?", Integer.class, tenantId.getValue(), c);
        if (clash != null && clash > 0) throw new IllegalStateException("A warehouse with code " + c + " already exists.");
        UUID id = UUID.randomUUID();
        // The first warehouse a tenant makes is its default.
        Integer any = jdbc.queryForObject("SELECT count(*) FROM whse_warehouses WHERE tenant_id = ? AND deleted_at IS NULL", Integer.class, tenantId.getValue());
        boolean first = any == null || any == 0;
        jdbc.update("INSERT INTO whse_warehouses (id, tenant_id, code, name, address, is_default) VALUES (?,?,?,?,?,?)", id, tenantId.getValue(), c, n, blankToNull(address), first);
        audit(tenantId, actor, "WHSE_WAREHOUSE_CREATED", id, "{\"code\":\"" + c + "\"}");
        return get(tenantId, id);
    }

    @Transactional
    public Warehouse update(TenantId tenantId, UUID id, String name, String address, UUID actor) {
        requireActor(actor);
        get(tenantId, id);
        jdbc.update("UPDATE whse_warehouses SET name = ?, address = ?, updated_at = now() WHERE tenant_id = ? AND id = ?", WhseWarehouseRules.name(name), blankToNull(address), tenantId.getValue(), id);
        audit(tenantId, actor, "WHSE_WAREHOUSE_UPDATED", id, "{}");
        return get(tenantId, id);
    }

    /** Makes a warehouse the default (and switches it on if it was off). The previous default stays as an ordinary warehouse. */
    @Transactional
    public Warehouse makeDefault(TenantId tenantId, UUID id, UUID actor) {
        requireActor(actor);
        get(tenantId, id);
        jdbc.update("UPDATE whse_warehouses SET is_default = FALSE, updated_at = now() WHERE tenant_id = ? AND is_default AND id <> ?", tenantId.getValue(), id);
        jdbc.update("UPDATE whse_warehouses SET is_default = TRUE, active = TRUE, updated_at = now() WHERE tenant_id = ? AND id = ?", tenantId.getValue(), id);
        audit(tenantId, actor, "WHSE_WAREHOUSE_DEFAULT_SET", id, "{}");
        return get(tenantId, id);
    }

    @Transactional
    public Warehouse setActive(TenantId tenantId, UUID id, boolean active, UUID actor) {
        requireActor(actor);
        Warehouse w = get(tenantId, id);
        if (!active) WhseWarehouseRules.checkDeactivate(w.isDefault(), w.stockOnHand());
        if (active && !w.active()) requireRoom(tenantId, WhsePackageRules.Limit.WAREHOUSES);
        jdbc.update("UPDATE whse_warehouses SET active = ?, updated_at = now() WHERE tenant_id = ? AND id = ?", active, tenantId.getValue(), id);
        audit(tenantId, actor, active ? "WHSE_WAREHOUSE_ACTIVATED" : "WHSE_WAREHOUSE_DEACTIVATED", id, "{}");
        return get(tenantId, id);
    }

    /** Number of warehouses that are switched on, for the package limit. */
    @Transactional(readOnly = true)
    public int activeCount(TenantId tenantId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM whse_warehouses WHERE tenant_id = ? AND active AND deleted_at IS NULL", Integer.class, tenantId.getValue());
        return n == null ? 0 : n;
    }

    private void audit(TenantId t, UUID actor, String action, UUID subject, String afterJson) {
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, action, item_type, item_id, after_json) VALUES (?, 'STAFF', ?, ?, 'WHSE_WAREHOUSE', ?, ?::jsonb)", t.getValue(), actor, action, subject, afterJson);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this change cannot be recorded.");
    }
}
