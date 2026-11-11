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

    /**
     * The warehouse a map action works in: the one asked for (it must be this tenant's and switched on), else the default. Null when the tenant has no
     * warehouse yet, which leaves the action unfiltered, exactly as before warehouses existed.
     */
    private UUID wh(UUID t, UUID requested) {
        if (requested != null) {
            List<Boolean> active = jdbc.query("SELECT active FROM whse_warehouses WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL", (rs, i) -> rs.getBoolean(1), t, requested);
            if (active == null || active.isEmpty()) throw new ResourceNotFoundException("WhseWarehouse", requested.toString());
            if (!active.get(0)) throw new HandyFlowException("That warehouse is switched off.", HttpStatus.CONFLICT, "WAREHOUSE_INACTIVE");
            return requested;
        }
        List<UUID> d = jdbc.query("SELECT id FROM whse_warehouses WHERE tenant_id = ? AND is_default AND deleted_at IS NULL", (rs, i) -> rs.getObject(1, UUID.class), t);
        return d == null || d.isEmpty() ? null : d.get(0);
    }

    private static boolean in(UUID wh, WhseLocation l) { return wh == null || wh.equals(l.getWarehouseId()); }

    @Transactional(readOnly = true)
    public List<PreviewSlot> preview(TenantId tenantId, WhseFloorLayout.Spec spec) { return preview(tenantId, spec, null); }

    @Transactional(readOnly = true)
    public List<PreviewSlot> preview(TenantId tenantId, WhseFloorLayout.Spec spec, UUID warehouseId) {
        UUID t = tenantId.getValue();
        UUID w = wh(t, warehouseId);
        return check(t, w, WhseFloorLayout.generate(spec, nextSequence(t, w)));
    }

    /** Marks each slot that clashes with an existing code or cell. */
    private List<PreviewSlot> check(UUID t, UUID w, List<WhseFloorLayout.Slot> slots) {
        // Codes stay reserved even for deleted locations (the unique constraint covers every row); cells are only taken by live ones.
        Set<String> codes = new HashSet<>(jdbc.queryForList("SELECT code FROM whse_locations WHERE tenant_id = ?", String.class, t));
        // A cell is per warehouse: another warehouse can have a bay in the same place.
        Set<String> cells = new HashSet<>(jdbc.query("SELECT pos_x, pos_y, coalesce(level, 0) FROM whse_locations WHERE tenant_id = ? AND pos_x IS NOT NULL AND deleted_at IS NULL AND (?::uuid IS NULL OR warehouse_id = ?::uuid)",
                (rs, i) -> rs.getInt(1) + ":" + rs.getInt(2) + ":" + rs.getInt(3), t, w, w));
        List<PreviewSlot> out = new ArrayList<>();
        for (WhseFloorLayout.Slot s : slots) {
            String conflict = null;
            if (codes.contains(s.code())) conflict = "Code " + s.code() + " already exists.";
            else if (cells.contains(s.x() + ":" + s.y() + ":" + s.level())) conflict = "Cell " + s.x() + "," + s.y() + " level " + s.level() + " is already used.";
            out.add(new PreviewSlot(s, conflict));
        }
        return out;
    }

    /** Saves the slots as new locations, or nothing when any code or cell is already taken. */
    private void createAll(UUID t, UUID w, List<WhseFloorLayout.Slot> slots, BigDecimal capacityUnits) {
        List<PreviewSlot> checked = check(t, w, slots);
        List<String> conflicts = checked.stream().map(PreviewSlot::conflict).filter(c -> c != null).toList();
        if (!conflicts.isEmpty()) {
            String shown = String.join(" ", conflicts.stream().limit(3).toList());
            throw new HandyFlowException(conflicts.size() + " of " + checked.size() + " locations clash with existing ones, so nothing was created. " + shown
                    + (conflicts.size() > 3 ? " ..." : ""), HttpStatus.CONFLICT, "FLOOR_LAYOUT_CONFLICT");
        }
        for (WhseFloorLayout.Slot s : slots) {
            WhseLocation l = WhseLocation.create(t, s.code(), s.zone(), null, capacityUnits);
            l.applyLayout(s.aisle(), s.bay(), s.level(), s.type(), s.pickSequence(), s.x(), s.y());
            if (w != null) l.assignWarehouse(w);
            repository.save(l);
        }
    }

    /** Creates the whole block, or nothing when any code or cell is already taken. */
    @Transactional
    public int generate(TenantId tenantId, WhseFloorLayout.Spec spec, BigDecimal capacityUnits, UUID actor) { return generate(tenantId, spec, capacityUnits, actor, null); }

    @Transactional
    public int generate(TenantId tenantId, WhseFloorLayout.Spec spec, BigDecimal capacityUnits, UUID actor, UUID warehouseId) {
        if (capacityUnits != null && capacityUnits.signum() < 0) throw new IllegalArgumentException("Capacity cannot be negative.");
        UUID w = wh(tenantId.getValue(), warehouseId);
        List<WhseFloorLayout.Slot> slots = WhseFloorLayout.generate(spec, nextSequence(tenantId.getValue(), w));
        createAll(tenantId.getValue(), w, slots, capacityUnits);
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, action, item_type, after_json) VALUES (?, 'STAFF', ?, 'WHSE_LOCATIONS_GENERATED', 'WHSE_LOCATION', ?::jsonb)",
                tenantId.getValue(), actor, "{\"count\":" + slots.size() + ",\"aisles\":" + spec.aisles().size() + ",\"zone\":" + quote(spec.zone()) + "}");
        return slots.size();
    }

    // ── editing after generation ────────────────────────────────────────────

    /**
     * Moves every level of the bay at one cell to another cell (drag on the map). Codes, aisle, bay and levels do not change, only
     * where the bay is drawn. The target must be free at each of those levels; nothing moves otherwise.
     */
    @Transactional
    public int moveCell(TenantId tenantId, int fromX, int fromY, int toX, int toY, UUID actor) { return moveCell(tenantId, fromX, fromY, toX, toY, actor, null); }

    @Transactional
    public int moveCell(TenantId tenantId, int fromX, int fromY, int toX, int toY, UUID actor, UUID warehouseId) {
        UUID t = tenantId.getValue();
        UUID w = wh(t, warehouseId);
        if (fromX < 0 || fromY < 0 || toX < 0 || toY < 0) throw new IllegalArgumentException("A map position cannot be negative.");
        if (fromX == toX && fromY == toY) throw new IllegalArgumentException("That is where it already is.");
        List<WhseLocation> moving = atCell(t, w, fromX, fromY);
        if (moving.isEmpty()) throw new ResourceNotFoundException("Map cell", fromX + "," + fromY);
        Set<Integer> targetLevels = new HashSet<>();
        for (WhseLocation o : atCell(t, w, toX, toY)) targetLevels.add(o.getLevel() == null ? 0 : o.getLevel());
        for (WhseLocation l : moving) {
            if (targetLevels.contains(l.getLevel() == null ? 0 : l.getLevel())) {
                throw new HandyFlowException("Something is already at column " + toX + ", row " + toY + ". Move it first, or choose an empty cell.", HttpStatus.CONFLICT, "CELL_OCCUPIED");
            }
        }
        for (WhseLocation l : moving) {
            l.applyLayout(l.getAisle(), l.getBay(), l.getLevel(), l.getLocationType(), l.getPickSequence(), toX, toY);
            repository.save(l);
        }
        audit(t, actor, "WHSE_FLOOR_CELL_MOVED", "{\"from\":\"" + fromX + "," + fromY + "\",\"to\":\"" + toX + "," + toY + "\",\"locations\":" + moving.size() + "}");
        return moving.size();
    }

    /**
     * Changes every level of a bay at once: the location type, the capacity per level, or whether it is in use. At least one must be given.
     * A bay that still holds stock cannot be switched off.
     */
    @Transactional
    public int editBay(TenantId tenantId, int x, int y, String type, BigDecimal capacityUnits, boolean clearCapacity, Boolean active, UUID actor) {
        return editBay(tenantId, x, y, type, capacityUnits, clearCapacity, active, actor, null);
    }

    @Transactional
    public int editBay(TenantId tenantId, int x, int y, String type, BigDecimal capacityUnits, boolean clearCapacity, Boolean active, UUID actor, UUID warehouseId) {
        UUID t = tenantId.getValue();
        UUID w = wh(t, warehouseId);
        if (type == null && capacityUnits == null && !clearCapacity && active == null) throw new IllegalArgumentException("Choose something to change.");
        if (type != null && !WhseFloorLayout.TYPES.contains(type)) throw new IllegalArgumentException("Unknown location type.");
        if (capacityUnits != null && capacityUnits.signum() < 0) throw new IllegalArgumentException("Capacity cannot be negative.");
        if (capacityUnits != null && clearCapacity) throw new IllegalArgumentException("Give a capacity or clear it, not both.");
        List<WhseLocation> locations = atCell(t, w, x, y);
        if (locations.isEmpty()) throw new ResourceNotFoundException("Map cell", x + "," + y);
        if (Boolean.FALSE.equals(active)) {
            for (WhseLocation l : locations) {
                BigDecimal held = jdbc.queryForObject("SELECT coalesce(sum(qty_on_hand), 0) FROM whse_inventory WHERE tenant_id = ? AND location_id = ?", BigDecimal.class, t, l.getId());
                if (held != null && held.signum() > 0) {
                    throw new HandyFlowException(l.getCode() + " still holds stock, so this bay cannot be switched off. Move the stock first.", HttpStatus.CONFLICT, "LOCATION_HAS_STOCK");
                }
            }
        }
        for (WhseLocation l : locations) {
            if (type != null) l.applyLayout(l.getAisle(), l.getBay(), l.getLevel(), type, l.getPickSequence(), l.getPosX(), l.getPosY());
            if (capacityUnits != null || clearCapacity) l.update(l.getCode(), l.getZone(), l.getDescription(), clearCapacity ? null : capacityUnits);
            if (Boolean.TRUE.equals(active)) l.reactivate();
            if (Boolean.FALSE.equals(active)) l.deactivate();
            repository.save(l);
        }
        audit(t, actor, "WHSE_FLOOR_BAY_EDITED", "{\"cell\":\"" + x + "," + y + "\",\"type\":" + quote(type) + ",\"capacity\":"
                + (clearCapacity ? "\"cleared\"" : capacityUnits == null ? "null" : capacityUnits.toPlainString()) + ",\"active\":" + active + ",\"locations\":" + locations.size() + "}");
        return locations.size();
    }

    /** Adds levels on top of a bay, named like the generator names them (aisle-bay-level), with the bay's type, zone and picking order. */
    @Transactional
    public int addLevels(TenantId tenantId, int x, int y, int count, BigDecimal capacityUnits, UUID actor) { return addLevels(tenantId, x, y, count, capacityUnits, actor, null); }

    @Transactional
    public int addLevels(TenantId tenantId, int x, int y, int count, BigDecimal capacityUnits, UUID actor, UUID warehouseId) {
        UUID t = tenantId.getValue();
        UUID w = wh(t, warehouseId);
        if (count < 1 || count > 10) throw new IllegalArgumentException("Add between 1 and 10 levels.");
        if (capacityUnits != null && capacityUnits.signum() < 0) throw new IllegalArgumentException("Capacity cannot be negative.");
        List<WhseLocation> locations = atCell(t, w, x, y);
        if (locations.isEmpty()) throw new ResourceNotFoundException("Map cell", x + "," + y);
        WhseLocation top = locations.get(locations.size() - 1);
        if (top.getAisle() == null || top.getBay() == null) throw new IllegalArgumentException("This bay has no aisle and bay number, so level codes cannot be made for it.");
        int topLevel = top.getLevel() == null ? 0 : top.getLevel();
        if (topLevel + count > 10) throw new IllegalArgumentException("A bay can have at most 10 levels; this one has " + topLevel + ".");
        List<WhseFloorLayout.Slot> slots = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            int level = topLevel + i;
            slots.add(new WhseFloorLayout.Slot(WhseFloorLayout.code(top.getAisle(), top.getBay(), level), top.getZone(), top.getAisle(), top.getBay(), level,
                    top.getLocationType(), x, y, top.getPickSequence() == null ? 0 : top.getPickSequence()));
        }
        createAll(t, w, slots, capacityUnits != null ? capacityUnits : top.getCapacityUnits());
        audit(t, actor, "WHSE_FLOOR_LEVELS_ADDED", "{\"cell\":\"" + x + "," + y + "\",\"levels\":" + count + "}");
        return count;
    }

    /**
     * Adds bays to the end of an existing aisle, with the same number of levels, type and zone as its last bay, on the same row.
     * The new bays are last in the picking order (the walking order of the aisle is not renumbered).
     */
    @Transactional
    public int extendAisle(TenantId tenantId, String aisle, int addBays, BigDecimal capacityUnits, UUID actor) { return extendAisle(tenantId, aisle, addBays, capacityUnits, actor, null); }

    @Transactional
    public int extendAisle(TenantId tenantId, String aisle, int addBays, BigDecimal capacityUnits, UUID actor, UUID warehouseId) {
        UUID t = tenantId.getValue();
        UUID w = wh(t, warehouseId);
        String name = aisle == null ? "" : aisle.trim().toUpperCase();
        if (addBays < 1 || addBays > 99) throw new IllegalArgumentException("Add between 1 and 99 bays.");
        if (capacityUnits != null && capacityUnits.signum() < 0) throw new IllegalArgumentException("Capacity cannot be negative.");
        List<WhseLocation> inAisle = repository.findAllActive(t).stream()
                .filter(l -> in(w, l)).filter(l -> name.equals(l.getAisle()) && l.getBay() != null && l.getPosX() != null && l.getPosY() != null).toList();
        if (inAisle.isEmpty()) throw new ResourceNotFoundException("Aisle on the map", name);
        int lastBay = inAisle.stream().mapToInt(WhseLocation::getBay).max().orElseThrow();
        if (lastBay + addBays > 99) throw new IllegalArgumentException("An aisle can have at most 99 bays; " + name + " has " + lastBay + ".");
        List<WhseLocation> last = inAisle.stream().filter(l -> l.getBay() == lastBay).sorted(java.util.Comparator.comparingInt(l -> l.getLevel() == null ? 0 : l.getLevel())).toList();
        WhseLocation template = last.get(last.size() - 1);
        int levels = template.getLevel() == null ? 1 : template.getLevel();
        int seq = nextSequence(t, w);
        List<WhseFloorLayout.Slot> slots = new ArrayList<>();
        for (int b = 1; b <= addBays; b++) {
            for (int level = 1; level <= levels; level++) {
                slots.add(new WhseFloorLayout.Slot(WhseFloorLayout.code(name, lastBay + b, level), template.getZone(), name, lastBay + b, level,
                        template.getLocationType(), template.getPosX() + b, template.getPosY(), seq++));
            }
        }
        if (slots.size() > WhseFloorLayout.MAX_SLOTS) throw new IllegalArgumentException("That is more than " + WhseFloorLayout.MAX_SLOTS + " locations in one go.");
        createAll(t, w, slots, capacityUnits != null ? capacityUnits : template.getCapacityUnits());
        audit(t, actor, "WHSE_FLOOR_AISLE_EXTENDED", "{\"aisle\":" + quote(name) + ",\"bays\":" + addBays + ",\"locations\":" + slots.size() + "}");
        return slots.size();
    }

    private List<WhseLocation> atCell(UUID t, UUID w, int x, int y) {
        return repository.findAllActive(t).stream()
                .filter(l -> in(w, l))
                .filter(l -> l.getPosX() != null && l.getPosX() == x && l.getPosY() != null && l.getPosY() == y)
                .sorted(java.util.Comparator.comparingInt(l -> l.getLevel() == null ? 0 : l.getLevel()))
                .toList();
    }

    private void audit(UUID t, UUID actor, String action, String afterJson) {
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, action, item_type, after_json) VALUES (?, 'STAFF', ?, ?, 'WHSE_LOCATION', ?::jsonb)", t, actor, action, afterJson);
    }

    /** Moves a location to a cell, or sets its aisle, bay, level, type and picking order. A null position takes it off the map. */
    @Transactional
    public WhseLocation setLayout(TenantId tenantId, UUID id, String aisle, Integer bay, Integer level, String type, Integer pickSequence, Integer x, Integer y) {
        UUID t = tenantId.getValue();
        WhseLocation l = repository.findActiveById(t, id).orElseThrow(() -> new ResourceNotFoundException("WhseLocation", id.toString()));
        if (x != null && y != null) {
            Integer taken = jdbc.queryForObject("SELECT count(*) FROM whse_locations WHERE tenant_id = ? AND pos_x = ? AND pos_y = ? AND coalesce(level, 0) = ? AND id <> ? AND deleted_at IS NULL AND warehouse_id IS NOT DISTINCT FROM ?::uuid",
                    Integer.class, t, x, y, level == null ? 0 : level, id, l.getWarehouseId());
            if (taken != null && taken > 0) {
                throw new HandyFlowException("Another location already sits at " + x + "," + y + (level == null ? "" : " level " + level) + ".", HttpStatus.CONFLICT, "CELL_OCCUPIED");
            }
        }
        l.applyLayout(aisle, bay, level, type, pickSequence, x, y);
        return repository.save(l);
    }

    @Transactional(readOnly = true)
    public FloorMap floorMap(TenantId tenantId) { return floorMap(tenantId, null); }

    @Transactional(readOnly = true)
    public FloorMap floorMap(TenantId tenantId, UUID warehouseId) {
        UUID w = wh(tenantId.getValue(), warehouseId);
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
                 WHERE l.tenant_id = ? AND l.deleted_at IS NULL AND (?::uuid IS NULL OR l.warehouse_id = ?::uuid)
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
                }, tenantId.getValue(), w, w);
        int placed = (int) rows.stream().filter(r -> r.posX() != null).count();
        return new FloorMap(rows, placed, rows.size() - placed);
    }

    /** Stock as a share of capacity, one decimal; null when the location has no capacity set. */
    static BigDecimal utilisation(BigDecimal onHand, BigDecimal capacity) {
        if (capacity == null || capacity.signum() <= 0) return null;
        return onHand.multiply(BigDecimal.valueOf(100)).divide(capacity, 1, RoundingMode.HALF_UP);
    }

    private int nextSequence(UUID tenant, UUID w) {
        Integer max = jdbc.queryForObject("SELECT max(pick_sequence) FROM whse_locations WHERE tenant_id = ? AND deleted_at IS NULL AND (?::uuid IS NULL OR warehouse_id = ?::uuid)", Integer.class, tenant, w, w);
        return (max == null ? 0 : max) + 1;
    }

    private static String quote(String s) { return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
