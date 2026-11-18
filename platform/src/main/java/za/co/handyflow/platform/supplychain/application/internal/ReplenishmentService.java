package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.ReplenishmentRules.Recommendation;
import za.co.handyflow.platform.supplychain.dto.CreateRequisitionRequest;
import za.co.handyflow.platform.supplychain.dto.RequisitionResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Replenishment recommendations from how fast each item is actually used, not only its reorder point. Per item, across the
 * tenant's real stock locations (quarantine is left out): available stock, open purchase orders, open requisitions, the last
 * 90 days of sales and production use, and the lead time suppliers have actually taken over the last year. Recommends;
 * creates nothing unless asked to raise a DRAFT requisition from them, which then goes through the normal approval.
 */
@Service
@RequiredArgsConstructor
public class ReplenishmentService {

    private static final int DEMAND_DAYS = 90;

    public record Item(UUID catalogueItemId, String itemName, BigDecimal available, BigDecimal onOrder, BigDecimal requested, BigDecimal reorderLevel, BigDecimal dailyDemand,
                       BigDecimal daysCover, int leadTimeDays, BigDecimal suggestedQty, String urgency, String reason, UUID suggestedSupplierId, BigDecimal suggestedUnitCost,
                       String priceSource, String contractNumber) {}

    private record ContractPrice(UUID supplierId, BigDecimal price, BigDecimal minQty, String contractNumber) {}

    public record Pick(UUID catalogueItemId, BigDecimal qty) {}
    public record RaiseRequest(String title, String businessReason, String department, String costCentre, String projectRef, UUID deliverToLocation, List<Pick> items) {}

    private final JdbcTemplate jdbc;
    private final RequisitionAvailabilityService availability;
    private final RequisitionService requisitions;

    @Transactional(readOnly = true)
    public List<Item> recommendations(TenantId tenantId) {
        UUID tid = tenantId.getValue();
        Map<UUID, BigDecimal> demand = new HashMap<>();
        jdbc.query("""
                SELECT i.catalogue_item_id, SUM(-m.qty_change) AS used
                  FROM sc_stock_movements m JOIN sc_inventory i ON i.id = m.inventory_id
                 WHERE m.tenant_id = ? AND m.movement_type IN ('SALE','PRODUCTION_OUT') AND m.created_at >= now() - (? * interval '1 day')
                 GROUP BY i.catalogue_item_id""", rs -> { demand.put((UUID) rs.getObject(1), rs.getBigDecimal(2)); }, tid, DEMAND_DAYS);
        Map<UUID, Integer> lead = new HashMap<>();
        jdbc.query("""
                SELECT l.catalogue_item_id, AVG(gr.received_date - po.order_date) AS days
                  FROM sc_goods_receipts gr JOIN sc_purchase_orders po ON po.id = gr.purchase_order_id JOIN sc_gr_lines l ON l.goods_receipt_id = gr.id
                 WHERE gr.tenant_id = ? AND gr.status = 'POSTED' AND l.catalogue_item_id IS NOT NULL AND gr.received_date >= current_date - 365
                 GROUP BY l.catalogue_item_id""", rs -> { lead.put((UUID) rs.getObject(1), (int) Math.ceil(Math.max(rs.getDouble(2), 1))); }, tid);

        // Prices agreed in a contract that is in force today, cheapest first. A line with a minimum quantity applies only to an order of at least that size.
        Map<UUID, List<ContractPrice>> contractPrices = new HashMap<>();
        jdbc.query("""
                SELECT l.catalogue_item_id, c.supplier_id, l.agreed_unit_price, l.min_qty, c.contract_number
                  FROM sc_contract_lines l JOIN sc_contracts c ON c.id = l.contract_id
                 WHERE c.tenant_id = ? AND c.status = 'ACTIVE' AND current_date BETWEEN c.start_date AND c.end_date AND l.catalogue_item_id IS NOT NULL
                 ORDER BY l.agreed_unit_price, c.contract_number""", rs -> {
            contractPrices.computeIfAbsent((UUID) rs.getObject(1), k -> new ArrayList<>())
                    .add(new ContractPrice((UUID) rs.getObject(2), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getString(5)));
        }, tid);

        List<Item> out = new ArrayList<>();
        jdbc.query("""
                SELECT i.catalogue_item_id,
                       COALESCE(ci.name, 'Stock item') AS item_name,
                       SUM(i.qty_on_hand - i.qty_reserved) AS available, SUM(i.reorder_point) AS rp, MAX(i.reorder_qty) AS rq, COALESCE(SUM(i.max_stock_level), 0) AS mx,
                       (SELECT si.supplier_id FROM sc_supplier_items si WHERE si.tenant_id = i.tenant_id AND si.catalogue_item_id = i.catalogue_item_id
                         ORDER BY si.is_preferred DESC, si.unit_cost ASC LIMIT 1) AS supplier_id,
                       (SELECT si.unit_cost FROM sc_supplier_items si WHERE si.tenant_id = i.tenant_id AND si.catalogue_item_id = i.catalogue_item_id
                         ORDER BY si.is_preferred DESC, si.unit_cost ASC LIMIT 1) AS unit_cost
                  FROM sc_inventory i
                  JOIN sc_stock_locations loc ON loc.id = i.location_id AND NOT loc.is_quarantine
                  LEFT JOIN catalogue_items ci ON ci.id = i.catalogue_item_id
                 WHERE i.tenant_id = ?
                 GROUP BY i.tenant_id, i.catalogue_item_id, ci.name
                 ORDER BY ci.name""", rs -> {
            UUID item = (UUID) rs.getObject("catalogue_item_id");
            BigDecimal used = demand.getOrDefault(item, BigDecimal.ZERO);
            BigDecimal rp = rs.getBigDecimal("rp");
            if (rp.signum() <= 0 && used.signum() <= 0) return;                 // nothing to base a recommendation on
            var av = availability.forItem(tid, item, null);
            BigDecimal daily = used.divide(BigDecimal.valueOf(DEMAND_DAYS), 4, RoundingMode.HALF_UP);
            int leadDays = lead.getOrDefault(item, ReplenishmentRules.DEFAULT_LEAD_TIME_DAYS);
            Recommendation r = ReplenishmentRules.recommend(new ReplenishmentRules.Input(rs.getBigDecimal("available"), av.onOrder(), av.alreadyRequested(), rp,
                    rs.getBigDecimal("rq"), rs.getBigDecimal("mx"), daily, leadDays));
            if (r == null) return;
            UUID supplier = (UUID) rs.getObject("supplier_id");
            BigDecimal price = rs.getBigDecimal("unit_cost");
            String source = supplier != null ? "SUPPLIER_ITEM" : null, contractNumber = null;
            ContractPrice agreed = contractPrices.getOrDefault(item, List.of()).stream()
                    .filter(c -> c.minQty() == null || r.orderQty().compareTo(c.minQty()) >= 0).findFirst().orElse(null);
            if (agreed != null) { supplier = agreed.supplierId(); price = agreed.price(); source = "CONTRACT"; contractNumber = agreed.contractNumber(); }
            out.add(new Item(item, rs.getString("item_name"), rs.getBigDecimal("available"), av.onOrder(), av.alreadyRequested(), r.reorderLevel(), daily, r.daysCover(), leadDays,
                    r.orderQty(), r.urgency().name(), r.reason(), supplier, price, source, contractNumber));
        }, tid);
        out.sort(Comparator.comparing((Item i) -> ReplenishmentRules.Urgency.valueOf(i.urgency())).thenComparing(Item::itemName));
        return out;
    }

    /** A DRAFT stock-replenishment requisition for the chosen items, at the quantities currently recommended (a pick may ask for a different quantity). */
    @Transactional
    public RequisitionResponse raiseRequisition(TenantId tenantId, UUID userId, String userName, RaiseRequest req) {
        if (req == null || req.items() == null || req.items().isEmpty()) throw new HandyFlowException("Choose at least one item", HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        Map<UUID, Item> recommended = new HashMap<>();
        recommendations(tenantId).forEach(i -> recommended.put(i.catalogueItemId(), i));
        List<CreateRequisitionRequest.Line> lines = new ArrayList<>();
        for (Pick p : req.items()) {
            Item i = recommended.get(p.catalogueItemId());
            if (i == null) throw new HandyFlowException("That item no longer needs replenishing", HttpStatus.CONFLICT, "NOT_RECOMMENDED");
            BigDecimal qty = p.qty() != null && p.qty().signum() > 0 ? p.qty() : i.suggestedQty();
            lines.add(new CreateRequisitionRequest.Line(i.catalogueItemId(), i.itemName(), i.reason(), qty,
                    i.suggestedUnitCost() != null ? i.suggestedUnitCost() : BigDecimal.ZERO, BigDecimal.valueOf(15), i.suggestedSupplierId()));
        }
        String title = req.title() != null && !req.title().isBlank() ? req.title().trim() : "Stock replenishment";
        String reason = req.businessReason() != null && !req.businessReason().isBlank() ? req.businessReason().trim() : "Stock at or below the level needed to cover lead time";
        return requisitions.create(tenantId, userId, userName, new CreateRequisitionRequest("STOCK_REPLENISHMENT", title, reason, req.department(), req.costCentre(),
                req.projectRef(), req.deliverToLocation(), null, "ZAR", lines));
    }
}
