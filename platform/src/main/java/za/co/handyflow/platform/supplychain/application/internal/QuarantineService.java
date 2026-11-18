package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.model.ScInventory;
import za.co.handyflow.platform.supplychain.domain.model.ScStockMovement;
import za.co.handyflow.platform.supplychain.domain.repository.ScInventoryRepository;
import za.co.handyflow.platform.supplychain.domain.repository.ScStockMovementRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Stock that fails inspection is moved out of the receiving location into the tenant's Quarantine location, so it cannot
 * be issued or counted as available. A hold ends in one of three ways: released back to where it came from, taken out
 * with a supplier return (the return's dispatch draws from the hold first), or written off. Movements are TRANSFER_OUT /
 * TRANSFER_IN (reference QUARANTINE) and WASTE / RETURN_OUT out of quarantine, so history is never deleted.
 * Inventory rows are locked in (item) order, as goods receipts and returns do.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuarantineService {

    public record Failed(UUID catalogueItemId, String itemName, BigDecimal qty, BigDecimal unitCost, String defectCode) {}
    public record Hold(UUID id, UUID inspectionId, UUID goodsReceiptId, UUID catalogueItemId, String itemName, BigDecimal qty, BigDecimal released,
                       BigDecimal returned, BigDecimal writtenOff, BigDecimal open, String status, String defectCode, Instant createdAt) {}

    private final JdbcTemplate jdbc;
    private final ScInventoryRepository inventoryRepo;
    private final ScStockMovementRepository movementRepo;

    /** Moves what is still on hand of each failed quantity into quarantine. Stock already used cannot be quarantined: that part is left (and logged). */
    @Transactional
    public int hold(TenantId tenantId, UUID userId, String userName, UUID inspectionId, UUID receiptId, List<Failed> failed) {
        UUID tid = tenantId.getValue();
        List<Failed> ordered = failed.stream().filter(f -> f.catalogueItemId() != null && f.qty().signum() > 0)
                .sorted(Comparator.comparing(Failed::catalogueItemId, GoodsReceivingService.UUID_ORDER)).toList();
        if (ordered.isEmpty()) return 0;
        UUID source = jdbc.query("SELECT received_to FROM sc_goods_receipts WHERE tenant_id = ? AND id = ?", (rs, n) -> (UUID) rs.getObject(1), tid, receiptId)
                .stream().findFirst().orElseThrow(() -> new HandyFlowException("Goods receipt not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        UUID quarantine = quarantineLocation(tid);
        int held = 0;
        for (Failed f : ordered) {
            Optional<ScInventory> src = inventoryRepo.lockByTenantIdAndLocationIdAndCatalogueItemId(tid, source, f.catalogueItemId());
            BigDecimal qty = src.map(i -> f.qty().min(i.getQtyOnHand().max(BigDecimal.ZERO))).orElse(BigDecimal.ZERO);
            if (qty.signum() <= 0) { log.warn("[SCM] Nothing left to quarantine for {} on inspection {}: the stock has been used", f.itemName(), inspectionId); continue; }
            ScInventory out = src.get();
            move(tid, out, "TRANSFER_OUT", qty.negate(), f.unitCost(), inspectionId, userId, userName);
            ScInventory in = inventoryRepo.lockByTenantIdAndLocationIdAndCatalogueItemId(tid, quarantine, f.catalogueItemId())
                    .orElseGet(() -> inventoryRepo.save(ScInventory.create(tid, quarantine, f.catalogueItemId())));
            move(tid, in, "TRANSFER_IN", qty, f.unitCost(), inspectionId, userId, userName);
            jdbc.update("""
                    INSERT INTO sc_quarantine_holds (id, tenant_id, inspection_id, goods_receipt_id, catalogue_item_id, item_name, source_location_id, quarantine_location_id,
                                                     qty, unit_cost, defect_code, created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
                    UUID.randomUUID(), tid, inspectionId, receiptId, f.catalogueItemId(), f.itemName(), source, quarantine, qty, f.unitCost(), f.defectCode(), userId);
            held++;
        }
        return held;
    }

    /** Failed stock turned out acceptable (a concession, a re-check): back to the location it came from. */
    @Transactional
    public Hold release(TenantId tenantId, UUID holdId, BigDecimal qty, UUID userId, String userName, String reason) {
        requireReason(reason);
        UUID tid = tenantId.getValue();
        Map<String, Object> h = lock(tid, holdId);
        BigDecimal take = take(h, qty);
        UUID item = (UUID) h.get("catalogue_item_id");
        BigDecimal cost = (BigDecimal) h.get("unit_cost");
        List<UUID> ids = new ArrayList<>(List.of((UUID) h.get("quarantine_location_id"), (UUID) h.get("source_location_id")));
        ScInventory q = lockedRow(tid, ids.get(0), item, true);
        BigDecimal onHand = q.getQtyOnHand();
        if (onHand.compareTo(take) < 0) throw new HandyFlowException("Quarantine holds only " + onHand.stripTrailingZeros().toPlainString() + " of that item", HttpStatus.CONFLICT, "QUARANTINE_SHORT");
        move(tid, q, "TRANSFER_OUT", take.negate(), cost, (UUID) h.get("inspection_id"), userId, userName);
        ScInventory back = lockedRow(tid, ids.get(1), item, false);
        move(tid, back, "TRANSFER_IN", take, cost, (UUID) h.get("inspection_id"), userId, userName);
        close(tid, holdId, "qty_released", take);
        log.info("[SCM] Released {} of {} from quarantine ({})", take, h.get("item_name"), reason);
        return get(tenantId, holdId);
    }

    /** Destroyed or scrapped: stock leaves as WASTE. */
    @Transactional
    public Hold writeOff(TenantId tenantId, UUID holdId, BigDecimal qty, UUID userId, String userName, String reason) {
        requireReason(reason);
        UUID tid = tenantId.getValue();
        Map<String, Object> h = lock(tid, holdId);
        BigDecimal take = take(h, qty);
        ScInventory q = lockedRow(tid, (UUID) h.get("quarantine_location_id"), (UUID) h.get("catalogue_item_id"), true);
        if (q.getQtyOnHand().compareTo(take) < 0) throw new HandyFlowException("Quarantine holds only " + q.getQtyOnHand().stripTrailingZeros().toPlainString() + " of that item", HttpStatus.CONFLICT, "QUARANTINE_SHORT");
        move(tid, q, "WASTE", take.negate(), (BigDecimal) h.get("unit_cost"), (UUID) h.get("inspection_id"), userId, userName);
        close(tid, holdId, "qty_written_off", take);
        log.info("[SCM] Wrote off {} of {} from quarantine ({})", take, h.get("item_name"), reason);
        return get(tenantId, holdId);
    }

    /**
     * Called when a return raised from an inspection is dispatched: takes up to {@code qty} of the item out of that
     * inspection's open holds (oldest first) and returns how much came from quarantine. The caller takes the rest from the receiving location.
     */
    @Transactional
    public BigDecimal takeForReturn(UUID tid, UUID inspectionId, UUID item, BigDecimal qty, UUID returnId, String returnNumber, UUID userId, String userName) {
        BigDecimal remaining = qty, taken = BigDecimal.ZERO;
        List<Map<String, Object>> holds = jdbc.queryForList("SELECT id FROM sc_quarantine_holds WHERE tenant_id = ? AND inspection_id = ? AND catalogue_item_id = ? AND status = 'OPEN' ORDER BY created_at, id",
                tid, inspectionId, item);
        for (Map<String, Object> row : holds) {
            if (remaining.signum() <= 0) break;
            Map<String, Object> h = lock(tid, (UUID) row.get("id"));
            BigDecimal open = open(h);
            BigDecimal take = open.min(remaining);
            if (take.signum() <= 0) continue;
            ScInventory q = lockedRow(tid, (UUID) h.get("quarantine_location_id"), item, true);
            if (q.getQtyOnHand().compareTo(take) < 0) throw new HandyFlowException("Quarantine holds less than the return needs: adjust stock first", HttpStatus.CONFLICT, "QUARANTINE_SHORT");
            ScStockMovement mv = ScStockMovement.record(tid, q, "RETURN_OUT", take.negate(), (BigDecimal) h.get("unit_cost"), "SUPPLIER_RETURN", returnId, returnNumber, userId, userName);
            q.adjustQty(take.negate(), (BigDecimal) h.get("unit_cost"));
            inventoryRepo.save(q);
            movementRepo.save(mv);
            close(tid, (UUID) row.get("id"), "qty_returned", take);
            remaining = remaining.subtract(take);
            taken = taken.add(take);
        }
        return taken;
    }

    @Transactional(readOnly = true)
    public List<Hold> list(TenantId tenantId, String status) {
        List<Object> args = new ArrayList<>(List.of(tenantId.getValue()));
        String sql = "SELECT id FROM sc_quarantine_holds WHERE tenant_id = ?" + (status != null && !status.isBlank() ? " AND status = ?" : "") + " ORDER BY created_at DESC LIMIT 500";
        if (status != null && !status.isBlank()) args.add(status.toUpperCase());
        return jdbc.query(sql, (rs, n) -> (UUID) rs.getObject(1), args.toArray()).stream().map(i -> get(tenantId, i)).toList();
    }

    @Transactional(readOnly = true)
    public Hold get(TenantId tenantId, UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM sc_quarantine_holds WHERE tenant_id = ? AND id = ?", tenantId.getValue(), id);
        if (rows.isEmpty()) throw new HandyFlowException("Quarantine hold not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        Map<String, Object> h = rows.get(0);
        Object at = h.get("created_at");
        return new Hold(id, (UUID) h.get("inspection_id"), (UUID) h.get("goods_receipt_id"), (UUID) h.get("catalogue_item_id"), (String) h.get("item_name"), (BigDecimal) h.get("qty"),
                (BigDecimal) h.get("qty_released"), (BigDecimal) h.get("qty_returned"), (BigDecimal) h.get("qty_written_off"), open(h), (String) h.get("status"), (String) h.get("defect_code"),
                at instanceof Timestamp t ? t.toInstant() : null);
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private UUID quarantineLocation(UUID tid) {
        jdbc.update("INSERT INTO sc_stock_locations (id, tenant_id, name, location_type, is_default, active, is_quarantine) VALUES (?,?,'Quarantine','VIRTUAL',false,true,true) ON CONFLICT DO NOTHING",
                UUID.randomUUID(), tid);
        return jdbc.queryForObject("SELECT id FROM sc_stock_locations WHERE tenant_id = ? AND is_quarantine", UUID.class, tid);
    }

    private ScInventory lockedRow(UUID tid, UUID location, UUID item, boolean mustExist) {
        Optional<ScInventory> row = inventoryRepo.lockByTenantIdAndLocationIdAndCatalogueItemId(tid, location, item);
        if (row.isPresent()) return row.get();
        if (mustExist) throw new HandyFlowException("No stock record for that item at the quarantine location", HttpStatus.CONFLICT, "NO_STOCK_RECORD");
        return inventoryRepo.save(ScInventory.create(tid, location, item));
    }

    private void move(UUID tid, ScInventory inv, String type, BigDecimal delta, BigDecimal cost, UUID inspectionId, UUID userId, String userName) {
        ScStockMovement mv = ScStockMovement.record(tid, inv, type, delta, cost, "QUARANTINE", inspectionId, null, userId, userName);
        inv.adjustQty(delta, cost);
        inventoryRepo.save(inv);
        movementRepo.save(mv);
    }

    private Map<String, Object> lock(UUID tid, UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM sc_quarantine_holds WHERE tenant_id = ? AND id = ? FOR UPDATE", tid, id);
        if (rows.isEmpty()) throw new HandyFlowException("Quarantine hold not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        return rows.get(0);
    }

    private static BigDecimal open(Map<String, Object> h) {
        return ((BigDecimal) h.get("qty")).subtract((BigDecimal) h.get("qty_released")).subtract((BigDecimal) h.get("qty_returned")).subtract((BigDecimal) h.get("qty_written_off"));
    }

    private static BigDecimal take(Map<String, Object> h, BigDecimal qty) {
        BigDecimal open = open(h);
        if (qty == null || qty.signum() <= 0) throw new HandyFlowException("Say how much (above zero)", HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        if (qty.compareTo(open) > 0) throw new HandyFlowException("Only " + open.stripTrailingZeros().toPlainString() + " is still in quarantine on this hold", HttpStatus.CONFLICT, "MORE_THAN_HELD");
        return qty;
    }

    private void close(UUID tid, UUID id, String column, BigDecimal by) {
        jdbc.update("UPDATE sc_quarantine_holds SET " + column + " = " + column + " + ?, updated_at = now(), "
                + "status = CASE WHEN qty - (qty_released + qty_returned + qty_written_off + ?) <= 0 THEN 'CLOSED' ELSE status END WHERE tenant_id = ? AND id = ?", by, by, tid, id);
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new HandyFlowException("A reason is required", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
    }
}
