package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.dto.SupplierScorecardResponse;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * Read-only views for buyers and finance: where the money goes, what sourcing and contracts saved (and leaked), and which
 * suppliers perform. Amounts are as recorded, in the currency of the documents (ZAR unless a tenant buys in others), ex VAT.
 * "Spend" is approved or paid supplier invoices by invoice date: money the business has accepted it owes.
 */
@Service
@RequiredArgsConstructor
public class ScmAnalyticsService {

    public record Period(LocalDate from, LocalDate to) {}
    public record Amount(String label, BigDecimal amount, Double sharePct) {}
    public record SupplierSpend(UUID supplierId, String supplierName, BigDecimal invoiced, Double sharePct, int invoices) {}

    public record Spend(Period period, BigDecimal ordered, BigDecimal invoiced, BigDecimal paid, BigDecimal invoicedWithoutPo, Double withoutPoSharePct,
                        BigDecimal creditsReceived, Double top5SupplierSharePct, List<Amount> byMonth, List<SupplierSpend> bySupplier, List<Amount> byBudgetScope) {}

    public record RfqSaving(UUID rfqId, String rfqNumber, String title, BigDecimal estimate, BigDecimal awarded, BigDecimal highestBid, BigDecimal savedVsEstimate, BigDecimal savedVsHighestBid) {}
    public record ContractLeak(String orderNumber, String itemName, BigDecimal qty, BigDecimal unitCost, BigDecimal agreedPrice, BigDecimal overpaid) {}
    public record Savings(Period period, int rfqsAwarded, int rfqsWithEstimate, BigDecimal savedVsEstimate, BigDecimal savedVsHighestBid, List<RfqSaving> rfqs,
                          BigDecimal paidAboveContract, int linesAboveContract, List<ContractLeak> contractLeaks, BigDecimal creditsRecovered,
                          BigDecimal negotiatedSavings) {}

    public record SupplierView(UUID supplierId, String supplierName, BigDecimal invoiced, Double spendSharePct, Integer score, String rating, Double onTimePct, Double fillRatePct,
                               Double rejectionPct, Double inspectionPassPct, Double returnRatePct, boolean lowConfidence, String flag) {}

    private final JdbcTemplate jdbc;
    private final SupplierScorecardService scorecards;

    private static final String BILLED = "status IN ('APPROVED','PAID')";

    @Transactional(readOnly = true)
    public Spend spend(TenantId tenantId, LocalDate from, LocalDate to) {
        Period p = period(from, to);
        UUID tid = tenantId.getValue();
        Date f = Date.valueOf(p.from()), t = Date.valueOf(p.to());

        BigDecimal invoiced = sum("SELECT COALESCE(SUM(subtotal),0) FROM sc_supplier_invoices WHERE tenant_id = ? AND " + BILLED + " AND invoice_date BETWEEN ? AND ?", tid, f, t);
        BigDecimal paid = sum("SELECT COALESCE(SUM(subtotal),0) FROM sc_supplier_invoices WHERE tenant_id = ? AND status = 'PAID' AND invoice_date BETWEEN ? AND ?", tid, f, t);
        BigDecimal noPo = sum("SELECT COALESCE(SUM(subtotal),0) FROM sc_supplier_invoices WHERE tenant_id = ? AND " + BILLED + " AND purchase_order_id IS NULL AND invoice_date BETWEEN ? AND ?", tid, f, t);
        BigDecimal ordered = sum("SELECT COALESCE(SUM(subtotal),0) FROM sc_purchase_orders WHERE tenant_id = ? AND status NOT IN ('DRAFT','PENDING_APPROVAL','CANCELLED') AND order_date BETWEEN ? AND ?", tid, f, t);
        BigDecimal credits = sum("SELECT COALESCE(SUM(credit_amount),0) FROM sc_supplier_returns WHERE tenant_id = ? AND status = 'CREDITED' AND credited_at::date BETWEEN ? AND ?", tid, f, t);

        List<Amount> byMonth = jdbc.query("SELECT to_char(invoice_date, 'YYYY-MM') AS m, SUM(subtotal) FROM sc_supplier_invoices WHERE tenant_id = ? AND " + BILLED
                + " AND invoice_date BETWEEN ? AND ? GROUP BY 1 ORDER BY 1", (rs, n) -> new Amount(rs.getString(1), rs.getBigDecimal(2), AnalyticsRules.sharePct(rs.getBigDecimal(2), invoiced)), tid, f, t);

        List<SupplierSpend> suppliers = supplierSpend(tid, f, t, invoiced);
        Double top5 = AnalyticsRules.topShare(suppliers.stream().map(SupplierSpend::invoiced).toList(), 5, invoiced);

        // Where it was charged: what POs committed to a department / cost centre / project, plus no-PO invoices coded to one.
        List<Amount> scopes = jdbc.query("""
                SELECT label, SUM(amount) AS amount FROM (
                    SELECT COALESCE(NULLIF(department,''), NULLIF(cost_centre,''), NULLIF(project_ref,''), '(uncoded)') AS label, amount
                      FROM sc_budget_commitments WHERE tenant_id = ? AND spend_date BETWEEN ? AND ?
                    UNION ALL
                    SELECT COALESCE(NULLIF(department,''), NULLIF(cost_centre,''), NULLIF(project_ref,''), '(uncoded)'), subtotal
                      FROM sc_supplier_invoices WHERE tenant_id = ? AND purchase_order_id IS NULL AND status IN ('APPROVED','PAID') AND invoice_date BETWEEN ? AND ?
                ) x GROUP BY label ORDER BY 2 DESC LIMIT 20""",
                (rs, n) -> new Amount(rs.getString(1), rs.getBigDecimal(2), null), tid, f, t, tid, f, t);
        BigDecimal scopeTotal = scopes.stream().map(Amount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        scopes = scopes.stream().map(a -> new Amount(a.label(), a.amount(), AnalyticsRules.sharePct(a.amount(), scopeTotal))).toList();

        return new Spend(p, ordered, invoiced, paid, noPo, AnalyticsRules.sharePct(noPo, invoiced), credits, top5, byMonth, suppliers.stream().limit(15).toList(), scopes);
    }

    @Transactional(readOnly = true)
    public Savings savings(TenantId tenantId, LocalDate from, LocalDate to) {
        Period p = period(from, to);
        UUID tid = tenantId.getValue();
        Date f = Date.valueOf(p.from()), t = Date.valueOf(p.to());

        List<RfqSaving> rfqs = jdbc.query("""
                SELECT r.id, r.rfq_number, r.title,
                       CASE WHEN EXISTS (SELECT 1 FROM sc_rfq_lines l WHERE l.rfq_id = r.id AND l.estimated_unit_cost IS NULL) THEN NULL
                            ELSE (SELECT SUM(l.quantity * l.estimated_unit_cost) FROM sc_rfq_lines l WHERE l.rfq_id = r.id) END AS estimate,
                       (SELECT q.landed_cost FROM sc_quotations q WHERE q.id = r.awarded_quotation_id) AS awarded,
                       (SELECT MAX(q.landed_cost) FROM sc_quotations q WHERE q.rfq_id = r.id AND q.status = 'SUBMITTED') AS highest
                  FROM sc_rfqs r
                 WHERE r.tenant_id = ? AND r.status = 'AWARDED' AND r.awarded_at::date BETWEEN ? AND ?
                 ORDER BY r.awarded_at DESC""", (rs, n) -> {
            BigDecimal est = rs.getBigDecimal("estimate"), awarded = rs.getBigDecimal("awarded"), highest = rs.getBigDecimal("highest");
            return new RfqSaving((UUID) rs.getObject("id"), rs.getString("rfq_number"), rs.getString("title"), est, awarded, highest,
                    AnalyticsRules.saving(est, awarded), AnalyticsRules.saving(highest, awarded));
        }, tid, f, t);
        BigDecimal vsEstimate = rfqs.stream().map(RfqSaving::savedVsEstimate).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vsHighest = rfqs.stream().map(RfqSaving::savedVsHighestBid).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Orders placed with a supplier that has a contract covering the item, at a price above the agreed one.
        List<ContractLeak> leaks = jdbc.query("""
                SELECT po.order_number, l.item_name, l.qty_ordered, l.unit_cost, cl.agreed_unit_price
                  FROM sc_po_lines l
                  JOIN sc_purchase_orders po ON po.id = l.purchase_order_id
                  JOIN sc_contracts c ON c.tenant_id = po.tenant_id AND c.supplier_id = po.supplier_id AND c.status IN ('ACTIVE','EXPIRED') AND po.order_date BETWEEN c.start_date AND c.end_date
                  JOIN sc_contract_lines cl ON cl.contract_id = c.id AND cl.catalogue_item_id = l.catalogue_item_id
                 WHERE po.tenant_id = ? AND po.status NOT IN ('DRAFT','PENDING_APPROVAL','CANCELLED') AND po.order_date BETWEEN ? AND ? AND l.unit_cost > cl.agreed_unit_price
                 ORDER BY (l.unit_cost - cl.agreed_unit_price) * l.qty_ordered DESC""", (rs, n) -> new ContractLeak(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5),
                AnalyticsRules.overContract(rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(3))), tid, f, t);
        BigDecimal leaked = leaks.stream().map(ContractLeak::overpaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = sum("SELECT COALESCE(SUM(credit_amount),0) FROM sc_supplier_returns WHERE tenant_id = ? AND status = 'CREDITED' AND credited_at::date BETWEEN ? AND ?", tid, f, t);

        // Buyer price below the requisition estimate, recorded at conversion, on orders that were actually placed.
        BigDecimal negotiated = sum("""
                SELECT COALESCE(SUM(c.saving_amount),0) FROM sc_requisition_conversions c
                  JOIN sc_purchase_orders po ON po.id = c.purchase_order_id
                 WHERE c.tenant_id = ? AND po.status NOT IN ('DRAFT','PENDING_APPROVAL','CANCELLED') AND po.order_date BETWEEN ? AND ?""", tid, f, t);

        return new Savings(p, rfqs.size(), (int) rfqs.stream().filter(r -> r.savedVsEstimate() != null).count(), vsEstimate, vsHighest, rfqs,
                leaked, leaks.size(), leaks.stream().limit(20).toList(), credits, negotiated);
    }

    @Transactional(readOnly = true)
    public List<SupplierView> suppliers(TenantId tenantId, Integer months) {
        UUID tid = tenantId.getValue();
        int m = months != null && months > 0 && months <= 60 ? months : 12;
        LocalDate to = LocalDate.now(), from = to.minusMonths(m);
        Date f = Date.valueOf(from), t = Date.valueOf(to);
        BigDecimal total = sum("SELECT COALESCE(SUM(subtotal),0) FROM sc_supplier_invoices WHERE tenant_id = ? AND " + BILLED + " AND invoice_date BETWEEN ? AND ?", tid, f, t);
        Map<UUID, SupplierSpend> spend = new HashMap<>();
        supplierSpend(tid, f, t, total).forEach(s -> spend.put(s.supplierId(), s));

        List<SupplierView> out = new ArrayList<>();
        for (SupplierScorecardResponse c : scorecards.all(tenantId, m)) {
            SupplierSpend s = spend.get(c.supplierId());
            BigDecimal invoiced = s != null ? s.invoiced() : BigDecimal.ZERO;
            Double share = s != null ? s.sharePct() : null;
            out.add(new SupplierView(c.supplierId(), c.supplierName(), invoiced, share, c.score(), c.rating(), c.onTimePct(), c.fillRatePct(), c.rejectionPct(),
                    c.inspectionPassPct(), c.returnRatePct(), c.lowConfidence(), AnalyticsRules.supplierFlag(c.score(), share)));
        }
        out.sort(Comparator.comparing(SupplierView::invoiced).reversed().thenComparing(SupplierView::supplierName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private List<SupplierSpend> supplierSpend(UUID tid, Date f, Date t, BigDecimal total) {
        return jdbc.query("""
                SELECT i.supplier_id, COALESCE(s.name, 'Unknown supplier') AS name, SUM(i.subtotal) AS invoiced, count(*) AS n
                  FROM sc_supplier_invoices i LEFT JOIN sc_suppliers s ON s.id = i.supplier_id AND s.tenant_id = i.tenant_id
                 WHERE i.tenant_id = ? AND i.status IN ('APPROVED','PAID') AND i.invoice_date BETWEEN ? AND ?
                 GROUP BY i.supplier_id, s.name ORDER BY invoiced DESC""",
                (rs, n) -> new SupplierSpend((UUID) rs.getObject(1), rs.getString(2), rs.getBigDecimal(3), AnalyticsRules.sharePct(rs.getBigDecimal(3), total), rs.getInt(4)), tid, f, t);
    }

    private BigDecimal sum(String sql, Object... args) {
        BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args);
        return v != null ? v : BigDecimal.ZERO;
    }

    private static Period period(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusMonths(12);
        if (start.isAfter(end)) throw new HandyFlowException("The period cannot end before it starts", HttpStatus.BAD_REQUEST, "BAD_PERIOD");
        if (start.plusYears(5).isBefore(end)) throw new HandyFlowException("Choose a period of five years or less", HttpStatus.BAD_REQUEST, "BAD_PERIOD");
        return new Period(start, end);
    }
}
