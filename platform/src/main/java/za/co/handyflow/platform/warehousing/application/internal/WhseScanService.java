package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseLocation;
import za.co.handyflow.platform.warehousing.domain.repository.WhseLocationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns whatever a scanner (or a keyboard) typed into the thing it names. A scan can match several things, for example the same SKU
 * for two clients, so the answer is a list and the screen asks which one. Matching is exact apart from case; nothing is guessed.
 */
@Service
@RequiredArgsConstructor
public class WhseScanService {

    private static final int PER_TYPE = 10;

    private final JdbcTemplate jdbc;
    private final WhseLocationRepository locations;

    public record Match(String type, UUID id, String label, String detail, UUID clientId) {}

    public record StockLine(UUID locationId, String locationCode, UUID itemId, String sku, String description, String clientName,
                            String lotNumber, LocalDate expiryDate, BigDecimal onHand, BigDecimal allocated, String status) {}

    /** Everything the code could be. Types: LOCATION, ITEM, LOT, SERIAL, INBOUND, OUTBOUND, MANIFEST. */
    @Transactional(readOnly = true)
    public List<Match> resolve(TenantId tenantId, String raw) {
        String code = clean(raw);
        UUID t = tenantId.getValue();
        List<Match> out = new ArrayList<>();
        out.addAll(jdbc.query("SELECT id, code, zone FROM whse_locations WHERE tenant_id = ? AND deleted_at IS NULL AND (upper(code) = upper(?) OR barcode = ?) LIMIT " + PER_TYPE,
                (rs, i) -> new Match("LOCATION", rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("zone"), null), t, code, code));
        out.addAll(jdbc.query("""
                SELECT i.id, i.sku, i.description, i.client_id, c.trading_name FROM whse_items i JOIN whse_clients c ON c.id = i.client_id
                 WHERE i.tenant_id = ? AND i.deleted_at IS NULL AND (i.barcode = ? OR upper(i.sku) = upper(?)) ORDER BY i.sku LIMIT\040""" + PER_TYPE,
                (rs, i) -> new Match("ITEM", rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("description") + " · " + rs.getString("trading_name"),
                        rs.getObject("client_id", UUID.class)), t, code, code));
        out.addAll(jdbc.query("""
                SELECT l.id, l.lot_number, l.client_id, i.sku, l.expiry_date FROM whse_lots l JOIN whse_items i ON i.id = l.item_id
                 WHERE l.tenant_id = ? AND upper(l.lot_number) = upper(?) ORDER BY i.sku LIMIT\040""" + PER_TYPE,
                (rs, i) -> new Match("LOT", rs.getObject("id", UUID.class), rs.getString("lot_number"),
                        rs.getString("sku") + (rs.getDate("expiry_date") == null ? "" : " · expires " + rs.getDate("expiry_date")), rs.getObject("client_id", UUID.class)), t, code));
        out.addAll(jdbc.query("SELECT id, reference_number, status, client_id FROM whse_inbound_shipments WHERE tenant_id = ? AND upper(reference_number) = upper(?) LIMIT " + PER_TYPE,
                (rs, i) -> new Match("INBOUND", rs.getObject("id", UUID.class), rs.getString("reference_number"), rs.getString("status"), rs.getObject("client_id", UUID.class)), t, code));
        out.addAll(jdbc.query("SELECT id, order_reference, status, client_id FROM whse_outbound_orders WHERE tenant_id = ? AND upper(order_reference) = upper(?) LIMIT " + PER_TYPE,
                (rs, i) -> new Match("OUTBOUND", rs.getObject("id", UUID.class), rs.getString("order_reference"), rs.getString("status"), rs.getObject("client_id", UUID.class)), t, code));
        out.addAll(jdbc.query("SELECT id, manifest_number, status FROM whse_dispatch_manifests WHERE tenant_id = ? AND upper(manifest_number) = upper(?) LIMIT " + PER_TYPE,
                (rs, i) -> new Match("MANIFEST", rs.getObject("id", UUID.class), rs.getString("manifest_number"), rs.getString("status"), null), t, code));
        out.addAll(jdbc.query("""
                SELECT s.id, s.serial_number, s.status, s.client_id, i.sku, c.trading_name FROM whse_serials s
                  JOIN whse_items i ON i.id = s.item_id JOIN whse_clients c ON c.id = s.client_id
                 WHERE s.tenant_id = ? AND upper(s.serial_number) = upper(?) ORDER BY i.sku LIMIT\040""" + PER_TYPE,
                (rs, i) -> new Match("SERIAL", rs.getObject("id", UUID.class), rs.getString("serial_number"),
                        rs.getString("sku") + " · " + rs.getString("trading_name") + " · " + rs.getString("status").toLowerCase().replace('_', ' '), rs.getObject("client_id", UUID.class)), t, code));
        return out;
    }

    /** What is in a location, one line per item, lot and status. */
    @Transactional(readOnly = true)
    public List<StockLine> stockAtLocation(TenantId tenantId, UUID locationId) {
        return stock(tenantId.getValue(), "inv.location_id = ?", locationId);
    }

    /** Where an item is, soonest expiry first, then the fewest units first so small leftovers get used up. */
    @Transactional(readOnly = true)
    public List<StockLine> stockOfItem(TenantId tenantId, UUID itemId) {
        return stock(tenantId.getValue(), "inv.item_id = ?", itemId);
    }

    /** Where a lot is, with its expiry. */
    @Transactional(readOnly = true)
    public List<StockLine> stockOfLot(TenantId tenantId, UUID lotId) {
        return stock(tenantId.getValue(), "inv.lot_id = ?", lotId);
    }

    private List<StockLine> stock(UUID t, String where, UUID id) {
        return jdbc.query("""
                SELECT inv.location_id, loc.code, inv.item_id, it.sku, it.description, c.trading_name, lot.lot_number, lot.expiry_date,
                       inv.qty_on_hand, inv.qty_allocated, inv.status
                  FROM whse_inventory inv
                  JOIN whse_locations loc ON loc.id = inv.location_id
                  JOIN whse_items it ON it.id = inv.item_id
                  JOIN whse_clients c ON c.id = inv.client_id
                  LEFT JOIN whse_lots lot ON lot.id = inv.lot_id
                 WHERE inv.tenant_id = ? AND inv.qty_on_hand > 0 AND\040""" + where + """
                 ORDER BY lot.expiry_date NULLS LAST, inv.qty_on_hand, loc.code LIMIT 200""",
                (rs, i) -> new StockLine(rs.getObject("location_id", UUID.class), rs.getString("code"), rs.getObject("item_id", UUID.class), rs.getString("sku"),
                        rs.getString("description"), rs.getString("trading_name"), rs.getString("lot_number"),
                        rs.getDate("expiry_date") == null ? null : rs.getDate("expiry_date").toLocalDate(),
                        rs.getBigDecimal("qty_on_hand"), rs.getBigDecimal("qty_allocated"), rs.getString("status")), t, id);
    }

    /** Gives a location its own barcode, or clears it. A barcode that is already another location's barcode or code is refused, so a scan never means two places. */
    @Transactional
    public WhseLocation setLocationBarcode(TenantId tenantId, UUID locationId, String barcode) {
        UUID t = tenantId.getValue();
        WhseLocation l = locations.findActiveById(t, locationId).orElseThrow(() -> new ResourceNotFoundException("WhseLocation", locationId.toString()));
        String value = barcode == null || barcode.isBlank() ? null : clean(barcode);
        if (value != null) {
            Integer clash = jdbc.queryForObject("SELECT count(*) FROM whse_locations WHERE tenant_id = ? AND deleted_at IS NULL AND id <> ? AND (barcode = ? OR upper(code) = upper(?))",
                    Integer.class, t, locationId, value, value);
            if (clash != null && clash > 0) {
                throw new HandyFlowException("Another location already uses " + value + " as its barcode or code.", HttpStatus.CONFLICT, "BARCODE_IN_USE");
            }
        }
        l.setBarcode(value);
        return locations.save(l);
    }

    private static String clean(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Scan or type something first.");
        if (s.length() > 100) throw new IllegalArgumentException("That is too long to be a barcode or reference.");
        return s;
    }
}
