package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.supplychain.application.internal.RequisitionRules.Availability;
import za.co.handyflow.platform.supplychain.dto.RequisitionSuggestionResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only stock / open-order lookups for requisitions. Uses the authoritative sources only:
 * sc_inventory for stock, non-cancelled sent POs for what is already coming, and other open
 * requisitions for demand already in the pipeline. Never writes.
 */
@Service
@RequiredArgsConstructor
public class RequisitionAvailabilityService {

    private final JdbcTemplate jdbc;

    private static final String OPEN_PO = "('APPROVED','SENT','ACKNOWLEDGED','PARTIALLY_RECEIVED')";

    /** @param excludeRequisitionId the requisition being viewed, so it doesn't count against itself */
    public Availability forItem(UUID tenantId, UUID catalogueItemId, UUID excludeRequisitionId) {
        if (catalogueItemId == null) return Availability.NONE;

        BigDecimal onHand = jdbc.queryForObject(
                "SELECT COALESCE(SUM(qty_on_hand - qty_reserved), 0) FROM sc_inventory WHERE tenant_id = ? AND catalogue_item_id = ?",
                BigDecimal.class, tenantId, catalogueItemId);

        BigDecimal onOrder = jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.qty_ordered - l.qty_received), 0)
                FROM sc_po_lines l JOIN sc_purchase_orders po ON po.id = l.purchase_order_id
                WHERE po.tenant_id = ? AND l.catalogue_item_id = ? AND po.status IN """ + OPEN_PO + """
                  AND l.qty_ordered > l.qty_received
                """, BigDecimal.class, tenantId, catalogueItemId);

        BigDecimal requested = jdbc.queryForObject("""
                SELECT COALESCE(SUM(GREATEST(l.qty_requested - COALESCE(c.converted, 0), 0)), 0)
                FROM sc_requisition_lines l
                JOIN sc_requisitions r ON r.id = l.requisition_id
                LEFT JOIN (
                    SELECT conv.requisition_line_id, SUM(conv.qty) AS converted
                    FROM sc_requisition_conversions conv
                    JOIN sc_purchase_orders po ON po.id = conv.purchase_order_id AND po.status <> 'CANCELLED'
                    GROUP BY conv.requisition_line_id
                ) c ON c.requisition_line_id = l.id
                WHERE r.tenant_id = ? AND l.catalogue_item_id = ?
                  AND r.status IN ('PENDING_APPROVAL','APPROVED') AND r.id <> ?
                """, BigDecimal.class, tenantId, catalogueItemId, excludeRequisitionId);

        return new Availability(nz(onHand), nz(onOrder), nz(requested));
    }

    /**
     * Replenishment SUGGESTIONS — stock at or below its reorder point, net of what is already on
     * order. Never creates anything: the buyer reviews these and raises a requisition.
     * Reorder points are per inventory row (location); on-order is tenant-wide per item.
     */
    public List<RequisitionSuggestionResponse> suggestions(UUID tenantId) {
        List<RequisitionSuggestionResponse> out = new ArrayList<>();
        jdbc.query("""
                SELECT i.catalogue_item_id, i.location_id,
                       i.qty_on_hand - i.qty_reserved AS available, i.qty_in_transit,
                       i.reorder_point, i.reorder_qty,
                       COALESCE((SELECT SUM(l.qty_ordered - l.qty_received)
                                 FROM sc_po_lines l JOIN sc_purchase_orders po ON po.id = l.purchase_order_id
                                 WHERE po.tenant_id = i.tenant_id AND l.catalogue_item_id = i.catalogue_item_id
                                   AND po.status IN """ + OPEN_PO + """
                                   AND l.qty_ordered > l.qty_received), 0) AS on_order,
                       COALESCE((SELECT l.item_name FROM sc_po_lines l
                                 WHERE l.tenant_id = i.tenant_id AND l.catalogue_item_id = i.catalogue_item_id LIMIT 1),
                                (SELECT si.item_name FROM sc_supplier_items si
                                 WHERE si.tenant_id = i.tenant_id AND si.catalogue_item_id = i.catalogue_item_id LIMIT 1),
                                'Stock item') AS item_name,
                       (SELECT si.supplier_id FROM sc_supplier_items si
                        WHERE si.tenant_id = i.tenant_id AND si.catalogue_item_id = i.catalogue_item_id
                        ORDER BY si.is_preferred DESC, si.unit_cost ASC LIMIT 1) AS supplier_id,
                       (SELECT si.unit_cost FROM sc_supplier_items si
                        WHERE si.tenant_id = i.tenant_id AND si.catalogue_item_id = i.catalogue_item_id
                        ORDER BY si.is_preferred DESC, si.unit_cost ASC LIMIT 1) AS unit_cost
                FROM sc_inventory i
                WHERE i.tenant_id = ? AND i.reorder_point > 0 AND i.qty_on_hand <= i.reorder_point
                ORDER BY item_name
                """, rs -> {
            BigDecimal available = rs.getBigDecimal("available");
            BigDecimal onOrder = rs.getBigDecimal("on_order");
            BigDecimal reorderPoint = rs.getBigDecimal("reorder_point");
            BigDecimal covered = available.add(onOrder);
            if (covered.compareTo(reorderPoint) > 0) return;   // an open order already lifts it above the point
            BigDecimal qty = RequisitionRules.suggestedQty(rs.getBigDecimal("reorder_qty"), reorderPoint, covered);
            if (qty.signum() <= 0) return;
            out.add(new RequisitionSuggestionResponse(
                    (UUID) rs.getObject("catalogue_item_id"), rs.getString("item_name"),
                    (UUID) rs.getObject("location_id"), available, onOrder, reorderPoint, qty,
                    (UUID) rs.getObject("supplier_id"), rs.getBigDecimal("unit_cost")));
        }, tenantId);
        return out;
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
