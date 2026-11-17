package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.SupplierScoreRules.Metrics;
import za.co.handyflow.platform.supplychain.application.internal.SupplierScoreRules.Score;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplier;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierRepository;
import za.co.handyflow.platform.supplychain.dto.SupplierScorecardResponse;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * Supplier scorecards computed LIVE from transaction data over a rolling window — not from the
 * denormalised counters on ScSupplier (totalOrders / onTimeDeliveries / ...), which nothing in the
 * code base ever updated.
 * <ul>
 *  <li>On-time: sent, non-cancelled POs with a required-by date. FULLY_RECEIVED/INVOICED ones are
 *      on time if the last posted receipt is on/before the date; any other PO whose date has already
 *      passed counts as late.</li>
 *  <li>Fill rate: accepted quantity / ordered quantity across sent, non-cancelled POs.</li>
 *  <li>Quality: 1 - rejected/delivered across posted receipts.</li>
 *  <li>Invoice accuracy: share of invoices WITHOUT a supplier-attributable match exception
 *      (price / quantity / amount / VAT / total / possible-duplicate). Missing-GRN and similar
 *      holds are the buyer's side, so they don't count against the supplier.</li>
 *  <li>Lead time: order date to first posted receipt.</li>
 * </ul>
 * Not covered yet: dispute-resolution time, price competitiveness.
 */
@Service
@RequiredArgsConstructor
public class SupplierScorecardService {

    private static final int LOW_CONFIDENCE_BELOW = 5;

    private final JdbcTemplate jdbc;
    private final ScSupplierRepository supplierRepo;
    private final SupplierComplianceService compliance;

    @Value("${app.scm.scorecard.default-months:12}")
    private int defaultMonths;

    private record Delivery(int onTime, int late, Double avgLead) {}
    private record Qty(double ordered, double accepted) {}
    private record Quality(double delivered, double rejected) {}
    private record Invoices(int total, int withErrors) {}

    @Transactional(readOnly = true)
    public SupplierScorecardResponse one(TenantId tenantId, UUID supplierId, Integer months) {
        ScSupplier s = supplierRepo.findByTenantIdAndId(tenantId.getValue(), supplierId)
                .orElseThrow(() -> new HandyFlowException("Supplier not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        return build(tenantId, List.of(s), months).get(0);
    }

    /** Every supplier in the tenant, best score first; suppliers with no data last. */
    @Transactional(readOnly = true)
    public List<SupplierScorecardResponse> all(TenantId tenantId, Integer months) {
        List<ScSupplier> suppliers = supplierRepo.findByTenantId(tenantId.getValue(), Pageable.unpaged()).getContent();
        List<SupplierScorecardResponse> out = new ArrayList<>(build(tenantId, suppliers, months));
        out.sort(Comparator.comparing((SupplierScorecardResponse r) -> r.score() == null ? -1 : r.score()).reversed()
                .thenComparing(SupplierScorecardResponse::supplierName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private List<SupplierScorecardResponse> build(TenantId tenantId, List<ScSupplier> suppliers, Integer monthsParam) {
        int months = monthsParam != null && monthsParam > 0 && monthsParam <= 60 ? monthsParam : defaultMonths;
        UUID tid = tenantId.getValue();
        Date since = Date.valueOf(LocalDate.now().minusMonths(months));

        Map<UUID, Delivery> deliveries = deliveries(tid, since);
        Map<UUID, Qty> qty = quantities(tid, since);
        Map<UUID, Quality> quality = quality(tid, since);
        Map<UUID, Invoices> invoices = invoices(tid, since);
        Map<UUID, SupplierComplianceRules.Overall> comp = compliance.overallForTenant(tenantId,
                suppliers.stream().map(ScSupplier::getId).toList());

        List<SupplierScorecardResponse> out = new ArrayList<>();
        for (ScSupplier s : suppliers) {
            Delivery d = deliveries.getOrDefault(s.getId(), new Delivery(0, 0, null));
            Qty q = qty.get(s.getId());
            Quality ql = quality.get(s.getId());
            Invoices inv = invoices.getOrDefault(s.getId(), new Invoices(0, 0));

            Double onTime = SupplierScoreRules.pct(d.onTime(), d.onTime() + d.late());
            Double fill = q == null ? null : SupplierScoreRules.pct(q.accepted(), q.ordered());
            Double rejectionPct = ql == null ? null : SupplierScoreRules.pct(ql.rejected(), ql.delivered());
            Double qualityPct = rejectionPct == null ? null : 100.0 - rejectionPct;
            Double invAcc = SupplierScoreRules.pct(inv.total() - inv.withErrors(), inv.total());

            Score score = SupplierScoreRules.score(new Metrics(onTime, fill, qualityPct, invAcc));
            out.add(new SupplierScorecardResponse(s.getId(), s.getName(), s.getStatus().name(), months,
                    d.onTime(), d.late(), round1(onTime), round1(fill), round1(rejectionPct), round1(invAcc),
                    inv.total(), inv.withErrors(), round1(d.avgLead()), score.score(), score.rating(),
                    d.onTime() + d.late() < LOW_CONFIDENCE_BELOW,
                    comp.getOrDefault(s.getId(), SupplierComplianceRules.Overall.NOT_REQUIRED).name()));
        }
        return out;
    }

    private Map<UUID, Delivery> deliveries(UUID tid, Date since) {
        Map<UUID, Delivery> m = new HashMap<>();
        jdbc.query("""
                WITH po_delivery AS (
                    SELECT po.id, po.supplier_id, po.required_by_date, po.order_date, po.status,
                           MIN(g.received_date) AS first_received, MAX(g.received_date) AS last_received
                    FROM sc_purchase_orders po
                    LEFT JOIN sc_goods_receipts g ON g.purchase_order_id = po.id AND g.status = 'POSTED'
                    WHERE po.tenant_id = ? AND po.sent_at IS NOT NULL AND po.status <> 'CANCELLED' AND po.order_date >= ?
                    GROUP BY po.id
                )
                SELECT supplier_id,
                  COUNT(*) FILTER (WHERE required_by_date IS NOT NULL AND status IN ('FULLY_RECEIVED','INVOICED')
                                     AND last_received <= required_by_date) AS on_time,
                  COUNT(*) FILTER (WHERE required_by_date IS NOT NULL AND (
                                     (status IN ('FULLY_RECEIVED','INVOICED') AND last_received > required_by_date)
                                  OR (status NOT IN ('FULLY_RECEIVED','INVOICED') AND required_by_date < CURRENT_DATE))) AS late,
                  AVG(first_received - order_date) FILTER (WHERE first_received IS NOT NULL) AS avg_lead
                FROM po_delivery GROUP BY supplier_id
                """, rs -> {
            Number lead = (Number) rs.getObject("avg_lead");
            m.put((UUID) rs.getObject("supplier_id"),
                    new Delivery(rs.getInt("on_time"), rs.getInt("late"), lead == null ? null : lead.doubleValue()));
        }, tid, since);
        return m;
    }

    private Map<UUID, Qty> quantities(UUID tid, Date since) {
        Map<UUID, Qty> m = new HashMap<>();
        jdbc.query("""
                SELECT po.supplier_id, SUM(l.qty_ordered) AS ordered, SUM(l.qty_received) AS accepted
                FROM sc_purchase_orders po JOIN sc_po_lines l ON l.purchase_order_id = po.id
                WHERE po.tenant_id = ? AND po.sent_at IS NOT NULL AND po.status <> 'CANCELLED' AND po.order_date >= ?
                GROUP BY po.supplier_id
                """, rs -> {
            m.put((UUID) rs.getObject("supplier_id"), new Qty(rs.getDouble("ordered"), rs.getDouble("accepted")));
        }, tid, since);
        return m;
    }

    private Map<UUID, Quality> quality(UUID tid, Date since) {
        Map<UUID, Quality> m = new HashMap<>();
        jdbc.query("""
                SELECT g.supplier_id, SUM(gl.qty_received) AS delivered, SUM(gl.qty_rejected) AS rejected
                FROM sc_goods_receipts g JOIN sc_gr_lines gl ON gl.goods_receipt_id = g.id
                WHERE g.tenant_id = ? AND g.status = 'POSTED' AND g.received_date >= ?
                GROUP BY g.supplier_id
                """, rs -> {
            m.put((UUID) rs.getObject("supplier_id"), new Quality(rs.getDouble("delivered"), rs.getDouble("rejected")));
        }, tid, since);
        return m;
    }

    private Map<UUID, Invoices> invoices(UUID tid, Date since) {
        Map<UUID, Invoices> m = new HashMap<>();
        jdbc.query("""
                SELECT i.supplier_id, COUNT(*) AS total,
                  COUNT(*) FILTER (WHERE EXISTS (
                      SELECT 1 FROM sc_match_exceptions e
                      WHERE e.supplier_invoice_id = i.id
                        AND e.exception_type IN ('PRICE_VARIANCE','QUANTITY_VARIANCE','AMOUNT_VARIANCE',
                                                 'TAX_VARIANCE','TOTAL_MISMATCH','POSSIBLE_DUPLICATE'))) AS with_errors
                FROM sc_supplier_invoices i
                WHERE i.tenant_id = ? AND i.invoice_date >= ?
                GROUP BY i.supplier_id
                """, rs -> {
            m.put((UUID) rs.getObject("supplier_id"), new Invoices(rs.getInt("total"), rs.getInt("with_errors")));
        }, tid, since);
        return m;
    }

    private static Double round1(Double v) {
        return v == null ? null : Math.round(v * 10.0) / 10.0;
    }
}
