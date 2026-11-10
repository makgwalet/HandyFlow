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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The floor map: where every location sits in the building and what it holds, plus generating a block of racking in one go and moving a
 * location to another cell. A map cell is one aisle/bay; the levels of that bay share it.
 */
@Service
@RequiredArgsConstructor
public class WhseFloorMapService {

    private final JdbcTemplate jdbc;
    private final WhseLocationRepository repository;

    public record PreviewSlot(WhseFloorLayout.Slot slot, String conflict) {}

    public record FloorLocation(UUID id, String code, String zone, String aisle, Integer bay, Integer level, String locationType, Integer pickSequence,
                                Integer posX, Integer posY, BigDecimal capacityUnits, boolean active, BigDecimal onHand, BigDecimal allocated,
                                BigDecimal held, int skuCount, LocalDate earliestExpiry, BigDecimal utilisationPct) {}

    public record FloorMap(List<FloorLocation> locations, int placed, int unplaced) {}

    @Transactional(readOnly = true)
    public List<PreviewSlot> preview(TenantId tenantId, WhseFloorLayout.Spec spec) {
        UUID t = tenantId.getValue();
        List<WhseFloorLayout.Slot> slots = WhseFloorLayout.generate(spec, nextSequence(t));
        // Codes stay reserved even for deleted locations (the unique constraint covers every row); cells are only taken by live ones.
        Set<String> codes = new HashSet<>(jdbc.queryForList("SELECT code FROM whse_locations WHERE tenant_id = ?", String.class, t));
        Set<String> cells = new HashSet<>(jdbc.query("SELECT pos_x, pos_y, coalesce(level, 0) FROM whse_locations WHERE tenant_id = ? AND pos_x IS NOT NULL AND deleted_at IS NULL",
                (rs, i) -> rs.getInt(1) + ":" + rs.getInt(2) + ":" + rs.getInt(3), t));
        List<PreviewSlot> out = new ArrayList<>();
        for (WhseFloorLayout.Slot s : slots) {
            String conflict = null;
            if (codes.contains(s.code())) conflict = "Code " + s.code() + " already exists.";
            else if (cells.contains(s.x() + ":" + s.y() + ":" + s.level())) conflict = "Cell " + s.x() + "," + s.y() + " level " + s.level() + " is already used.";
            out.add(new PreviewSlot(s, conflict));
        }
        return out;
    }

    /** Creates the whole block, or nothing when any code or cell is already taken. */
    @Transactional
    public int generate(TenantId tenantId, WhseFloorLayout.Spec spec, BigDecimal capacityUnits, UUID actor) {
        if (capacityUnits != null && capacityUnits.signum() < 0) throw new IllegalArgumentException("Capacity cannot be negative.");
        List<PreviewSlot> slots = preview(tenantId, spec);
        List<String> conflicts = slots.stream().map(PreviewSlot::conflict).filter(c -> c != null).toList();
        if (!conflicts.isEmpty()) {
            String shown = String.join(" ", conflicts.stream().limit(3).toList());
            throw new HandyFlowException(conflicts.size() + " of " + slots.size() + " locations clash with existing ones, so nothing was created. " + shown
                    + (conflicts.size() > 3 ? " ..." : ""), HttpStatus.CONFLICT, "FLOOR_LAYOUT_CONFLICT");
        }
        for (PreviewSlot p : slots) {
            WhseFloorLayout.Slot s = p.slot();
            WhseLocation l = WhseLocation.create(tenantId.getValue(), s.code(), s.zone(), null, capacityUnits);
            l.applyLayout(s.aisle(), s.bay(), s.level(), s.type(), s.pickSequence(), s.x(), s.y());
            repository.save(l);
        }
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, action, item_type, after_json) VALUES (?, 'STAFF', ?, 'WHSE_LOCATIONS_GENERATED', 'WHSE_LOCATION', ?::jsonb)",
                tenantId.getValue(), actor, "{\"count\":" + slots.size() + ",\"aisles\":" + spec.aisles().size() + ",\"zone\":" + quote(spec.zone()) + "}");
        return slots.size();
    }

    /** Moves a location to a cell, or sets its aisle, bay, level, type and picking order. A null position takes it off the map. */
    @Transactional
    public WhseLocation setLayout(TenantId tenantId, UUID id, String aisle, Integer bay, Integer level, String type, Integer pickSequence, Integer x, Integer y) {
        UUID t = tenantId.getValue();
        WhseLocation l = repository.findActiveById(t, id).orElseThrow(() -> new ResourceNotFoundException("WhseLocation", id.toString()));
        if (x != null && y != null) {
            Integer taken = jdbc.queryForObject("SELECT count(*) FROM whse_locations WHERE tenant_id = ? AND pos_x = ? AND pos_y = ? AND coalesce(level, 0) = ? AND id <> ? AND deleted_at IS NULL",
                    Integer.class, t, x, y, level == null ? 0 : level, id);
            if (taken != null && taken > 0) {
                throw new HandyFlowException("Another location already sits at " + x + "," + y + (level == null ? "" : " level " + level) + ".", HttpStatus.CONFLICT, "CELL_OCCUPIED");
            }
        }
        l.applyLayout(aisle, bay, level, type, pickSequence, x, y);
        return repository.save(l);
    }

    @Transactional(readOnly = true)
    public FloorMap floorMap(TenantId tenantId) {
        List<FloorLocation> rows = jdbc.query("""
                SELECT l.id, l.code, l.zone, l.aisle, l.bay, l.level, l.location_type, l.pick_sequence, l.pos_x, l.pos_y, l.capacity_units, l.active,
                       coalesce(sum(i.qty_on_hand), 0) AS on_hand,
                       coalesce(sum(i.qty_allocated), 0) AS allocated,
                       coalesce(sum(i.qty_on_hand) FILTER (WHERE i.status <> 'AVAILABLE'), 0) AS held,
                       count(DISTINCT i.item_id) FILTER (WHERE i.qty_on_hand > 0) AS skus,
                       min(lot.expiry_date) FILTER (WHERE i.qty_on_hand > 0) AS earliest_expiry
                  FROM whse_locations l
                  LEFT JOIN whse_inventory i ON i.location_id = l.id AND i.tenant_id = l.tenant_id
                  LEFT JOIN whse_lots lot ON lot.id = i.lot_id
                 WHERE l.tenant_id = ? AND l.deleted_at IS NULL
                 GROUP BY l.id
                 ORDER BY l.pick_sequence NULLS LAST, l.code""",
                (rs, n) -> {
                    BigDecimal cap = rs.getBigDecimal("capacity_units");
                    BigDecimal onHand = rs.getBigDecimal("on_hand");
                    return new FloorLocation(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("zone"), rs.getString("aisle"),
                            (Integer) rs.getObject("bay"), (Integer) rs.getObject("level"), rs.getString("location_type"), (Integer) rs.getObject("pick_sequence"),
                            (Integer) rs.getObject("pos_x"), (Integer) rs.getObject("pos_y"), cap, rs.getBoolean("active"), onHand, rs.getBigDecimal("allocated"),
                            rs.getBigDecimal("held"), rs.getInt("skus"), rs.getDate("earliest_expiry") == null ? null : rs.getDate("earliest_expiry").toLocalDate(),
                            utilisation(onHand, cap));
                }, tenantId.getValue());
        int placed = (int) rows.stream().filter(r -> r.posX() != null).count();
        return new FloorMap(rows, placed, rows.size() - placed);
    }

    /** Stock as a share of capacity, one decimal; null when the location has no capacity set. */
    static BigDecimal utilisation(BigDecimal onHand, BigDecimal capacity) {
        if (capacity == null || capacity.signum() <= 0) return null;
        return onHand.multiply(BigDecimal.valueOf(100)).divide(capacity, 1, RoundingMode.HALF_UP);
    }

    private int nextSequence(UUID tenant) {
        Integer max = jdbc.queryForObject("SELECT max(pick_sequence) FROM whse_locations WHERE tenant_id = ? AND deleted_at IS NULL", Integer.class, tenant);
        return (max == null ? 0 : max) + 1;
    }

    private static String quote(String s) { return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
