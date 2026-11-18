package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.supplychain.dto.PolicyRefinementDtos.SavingsItem;
import za.co.handyflow.platform.supplychain.dto.PolicyRefinementDtos.SavingsReport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Savings against catalogue list price: for every catalogue-linked line on a PO that has been approved (or later), what was
 * paid against the item's list price, in the base currency. Where an active contract covered the item on the PO date the
 * agreed price is also compared, so a line ordered above its contract price shows as leakage. Lines whose item has no list
 * price, or whose currency has no exchange rate, are counted and left out of the money, never guessed.
 */
@Service
@RequiredArgsConstructor
public class ScmSavingsService {

    private static final int MAX_LINES = 50_000;

    private final JdbcTemplate jdbc;
    private final ProcurementPolicy policy;

    @Transactional(readOnly = true)
    public SavingsReport report(UUID tid, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(364);
        if (start.isAfter(end)) throw new HandyFlowException("The start date is after the end date", HttpStatus.BAD_REQUEST, "BAD_RANGE");
        if (start.plusYears(3).isBefore(end)) throw new HandyFlowException("Choose a period of three years or less", HttpStatus.BAD_REQUEST, "BAD_RANGE");

        record Row(UUID item, String name, BigDecimal qty, BigDecimal list, BigDecimal contract, BigDecimal paid, boolean covered) {}
        int[] noList = {0}, noRate = {0};

        List<Row> rows = jdbc.query("""
                SELECT pl.catalogue_item_id, ci.name, pl.qty_ordered, ci.default_price, k.price AS contract_price,
                       pl.unit_cost, sc_fx_rate(po.tenant_id, po.currency, po.order_date) AS fx
                  FROM sc_po_lines pl
                  JOIN sc_purchase_orders po ON po.id = pl.purchase_order_id
                  JOIN catalogue_items ci ON ci.id = pl.catalogue_item_id AND ci.tenant_id = po.tenant_id
                  LEFT JOIN LATERAL (
                        SELECT MIN(cl.agreed_unit_price) AS price
                          FROM sc_contract_lines cl JOIN sc_contracts c ON c.id = cl.contract_id
                         WHERE c.tenant_id = po.tenant_id AND c.supplier_id = po.supplier_id
                           AND c.status IN ('ACTIVE','EXPIRED') AND po.order_date BETWEEN c.start_date AND c.end_date
                           AND cl.catalogue_item_id = pl.catalogue_item_id) k ON TRUE
                 WHERE po.tenant_id = ? AND po.order_date BETWEEN ? AND ?
                   AND po.status IN ('APPROVED','SENT','ACKNOWLEDGED','PARTIALLY_RECEIVED','FULLY_RECEIVED','INVOICED')
                 LIMIT ?
                """,
                (rs, n) -> {
                    BigDecimal fx = rs.getBigDecimal("fx");
                    BigDecimal list = rs.getBigDecimal("default_price");
                    if (fx == null) { noRate[0]++; return null; }
                    if (list == null || list.signum() <= 0) { noList[0]++; return null; }
                    BigDecimal contract = rs.getBigDecimal("contract_price");
                    return new Row((UUID) rs.getObject("catalogue_item_id"), rs.getString("name"), rs.getBigDecimal("qty_ordered"), list,
                            contract != null ? ScmPolicyRules.toBase(contract, fx) : null,
                            ScmPolicyRules.toBase(rs.getBigDecimal("unit_cost"), fx), contract != null);
                }, tid, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end), MAX_LINES).stream().filter(Objects::nonNull).toList();

        Map<UUID, BigDecimal[]> acc = new LinkedHashMap<>();   // qty, list, paid, saving, leakage
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, int[]> counts = new HashMap<>();             // lines, contract lines
        BigDecimal list = BigDecimal.ZERO, paid = BigDecimal.ZERO, saving = BigDecimal.ZERO, leakage = BigDecimal.ZERO;
        for (Row r : rows) {
            ScmPolicyRules.Savings s = ScmPolicyRules.savings(r.list(), r.contract(), r.paid(), r.qty());
            BigDecimal lv = r.list().multiply(r.qty()), pv = r.paid().multiply(r.qty());
            BigDecimal[] a = acc.computeIfAbsent(r.item(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            a[0] = a[0].add(r.qty()); a[1] = a[1].add(lv); a[2] = a[2].add(pv); a[3] = a[3].add(s.saving()); a[4] = a[4].add(s.leakage());
            names.put(r.item(), r.name());
            int[] c = counts.computeIfAbsent(r.item(), k -> new int[2]);
            c[0]++; if (r.covered()) c[1]++;
            list = list.add(lv); paid = paid.add(pv); saving = saving.add(s.saving()); leakage = leakage.add(s.leakage());
        }
        List<SavingsItem> items = new ArrayList<>();
        acc.forEach((id, a) -> items.add(new SavingsItem(id, names.get(id), a[0], a[1].setScale(2, java.math.RoundingMode.HALF_UP),
                a[2].setScale(2, java.math.RoundingMode.HALF_UP), a[3], a[4], counts.get(id)[0], counts.get(id)[1])));
        items.sort(Comparator.comparing(SavingsItem::saving).reversed());
        return new SavingsReport(start, end, policy.forTenant(tid).baseCurrency(), list.setScale(2, java.math.RoundingMode.HALF_UP),
                paid.setScale(2, java.math.RoundingMode.HALF_UP), saving, leakage, rows.size(), noList[0], noRate[0], items);
    }
}
