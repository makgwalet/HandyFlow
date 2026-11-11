package za.co.handyflow.platform.warehousing.application.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Chooses the warehouse an inbound shipment or outbound order belongs to. A document is created in the tenant's default warehouse (the database
 * does that); this moves it to another warehouse of the same tenant. It is allowed only while the document has had no effect on stock, which the
 * database also enforces.
 */
@Component
public class WhseDocumentWarehouse {

    /** The two tables that carry a warehouse. A fixed list, never built from input. */
    public enum Kind {
        INBOUND("whse_inbound_shipments", "shipment"),
        OUTBOUND("whse_outbound_orders", "order");

        final String table;
        final String noun;
        Kind(String table, String noun) { this.table = table; this.noun = noun; }
    }

    private final JdbcTemplate jdbc;

    public WhseDocumentWarehouse(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Puts the document in the given warehouse. A null warehouse leaves it where it is. */
    @Transactional
    public void assign(TenantId tenantId, Kind kind, UUID documentId, UUID warehouseId) {
        if (warehouseId == null) return;
        UUID t = tenantId.getValue();
        Integer open = jdbc.queryForObject(
                "SELECT count(*) FROM whse_warehouses WHERE tenant_id = ? AND id = ? AND active AND deleted_at IS NULL", Integer.class, t, warehouseId);
        if (open == null || open == 0) throw new IllegalArgumentException("That warehouse does not exist or is not active");
        int changed = jdbc.update("UPDATE " + kind.table + " SET warehouse_id = ? WHERE tenant_id = ? AND id = ?", warehouseId, t, documentId);
        if (changed == 0) throw new IllegalArgumentException("That " + kind.noun + " was not found");
    }
}
