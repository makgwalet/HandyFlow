package za.co.handyflow.platform.supplychain.application.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The company's procurement financial policy: how budgets measure VAT, and how far a buyer may raise a price above the
 * requisition estimate before a second person must approve it. A company's own row in sc_procurement_policy wins; a
 * company with no row gets the defaults (ex-VAT, 5% tolerance; app.scm.budget.include-vat=true makes the default INC_VAT).
 *
 * Budget basis: EX_VAT counts the net amount (plus any non-recoverable VAT when that box is ticked); INC_VAT counts the
 * full invoice total; NET_OF_RECOVERABLE counts the net amount plus the part of the VAT the company cannot claim back.
 * Cash commitment is always the VAT-inclusive total and is reported by AP, not by budgets.
 *
 * Changing the basis, the recoverable VAT %, the base currency, a supplier's VAT override or an exchange rate restates the
 * existing budget commitments in the same transaction, and the audit row says how many were restated.
 *
 * Per-category price tolerance (sc_category_price_tolerance) and per-supplier VAT recoverability (sc_supplier_vat) override
 * the company figures; foreign-currency documents are measured in the base currency at the rate on the document date.
 */
@Component
public class ProcurementPolicy {

    public static final String EX_VAT = "EX_VAT", INC_VAT = "INC_VAT", NET_OF_RECOVERABLE = "NET_OF_RECOVERABLE";

    public record Policy(String budgetBasis, BigDecimal vatRecoverablePct, boolean includeNonRecoverableVat,
                         BigDecimal priceIncreaseTolerancePct, String baseCurrency) {
        public Policy {
            baseCurrency = baseCurrency == null || baseCurrency.isBlank() ? "ZAR" : baseCurrency.trim().toUpperCase(Locale.ROOT);
        }
        public Policy(String budgetBasis, BigDecimal vatRecoverablePct, boolean includeNonRecoverableVat,
                      BigDecimal priceIncreaseTolerancePct) {
            this(budgetBasis, vatRecoverablePct, includeNonRecoverableVat, priceIncreaseTolerancePct, "ZAR");
        }
    }

    public record View(Policy effective, Policy defaults, boolean customised) {}

    private final JdbcTemplate jdbc;
    private final Policy defaults;

    public ProcurementPolicy(JdbcTemplate jdbc,
                             @Value("${app.scm.budget.include-vat:false}") boolean includeVat,
                             @Value("${app.scm.price.increase-tolerance-pct:5}") BigDecimal tolerancePct) {
        this.jdbc = jdbc;
        this.defaults = new Policy(includeVat ? INC_VAT : EX_VAT, BigDecimal.valueOf(100), true, tolerancePct);
    }

    public Policy forTenant(UUID tenantId) { return stored(tenantId).orElse(defaults); }

    public View view(UUID tenantId) {
        Optional<Policy> own = stored(tenantId);
        return new View(own.orElse(defaults), defaults, own.isPresent());
    }

    /**
     * Share of a document's VAT that counts towards a budget, 0..1. The budget amount is net + VAT x this factor.
     */
    public static BigDecimal vatFactor(Policy p) {
        return ScmPolicyRules.vatFactor(p.budgetBasis(), p.vatRecoverablePct(), p.includeNonRecoverableVat());
    }

    public static BigDecimal budgetAmount(BigDecimal net, BigDecimal vat, Policy p) {
        return net.add(vat.multiply(vatFactor(p))).setScale(2, RoundingMode.HALF_UP);
    }

    public static String problem(Policy p) {
        if (p == null || p.budgetBasis() == null || p.vatRecoverablePct() == null || p.priceIncreaseTolerancePct() == null)
            return "Budget basis, recoverable VAT % and price tolerance are required";
        if (!(EX_VAT.equals(p.budgetBasis()) || INC_VAT.equals(p.budgetBasis()) || NET_OF_RECOVERABLE.equals(p.budgetBasis())))
            return "Budget basis must be EX_VAT, INC_VAT or NET_OF_RECOVERABLE";
        if (p.vatRecoverablePct().signum() < 0 || p.vatRecoverablePct().compareTo(BigDecimal.valueOf(100)) > 0)
            return "Recoverable VAT must be between 0 and 100 %";
        if (p.priceIncreaseTolerancePct().signum() < 0 || p.priceIncreaseTolerancePct().compareTo(BigDecimal.valueOf(100)) > 0)
            return "The price increase tolerance must be between 0 and 100 %";
        String cur = ScmPolicyRules.currencyProblem(p.baseCurrency());
        if (cur != null) return "Base currency: " + cur;
        return null;
    }

    // ── Overrides and conversion ──────────────────────────────────────────────

    /** The price tolerance for a catalogue category: its own override, else the company's. A null category is the company's. */
    public BigDecimal toleranceFor(UUID tenantId, UUID categoryId) {
        BigDecimal company = forTenant(tenantId).priceIncreaseTolerancePct();
        if (categoryId == null) return company;
        BigDecimal own = jdbc.query("SELECT tolerance_pct FROM sc_category_price_tolerance WHERE tenant_id = ? AND category_id = ?",
                (rs, n) -> rs.getBigDecimal(1), tenantId, categoryId).stream().findFirst().orElse(null);
        return ScmPolicyRules.tolerance(company, own);
    }

    /** The price tolerance for a catalogue item, through its category. An item with no category uses the company's. */
    public BigDecimal toleranceForItem(UUID tenantId, UUID catalogueItemId) {
        if (catalogueItemId == null) return forTenant(tenantId).priceIncreaseTolerancePct();
        UUID category = jdbc.query("SELECT category_id FROM catalogue_items WHERE tenant_id = ? AND id = ?",
                (rs, n) -> (UUID) rs.getObject(1), tenantId, catalogueItemId).stream().findFirst().orElse(null);
        return toleranceFor(tenantId, category);
    }

    /** The VAT recoverability for a supplier: its own override, else the company's. */
    public BigDecimal vatRecoverablePctFor(UUID tenantId, UUID supplierId) {
        BigDecimal company = forTenant(tenantId).vatRecoverablePct();
        if (supplierId == null) return company;
        BigDecimal own = jdbc.query("SELECT vat_recoverable_pct FROM sc_supplier_vat WHERE tenant_id = ? AND supplier_id = ?",
                (rs, n) -> rs.getBigDecimal(1), tenantId, supplierId).stream().findFirst().orElse(null);
        return ScmPolicyRules.vatRecoverablePct(company, own);
    }

    /** Rate to the base currency on a date; null when the currency has no rate on or before the date. */
    public BigDecimal rateOrNull(UUID tenantId, String currency, LocalDate date) {
        return jdbc.queryForObject("SELECT sc_fx_rate(?, ?, ?)", BigDecimal.class, tenantId, currency,
                java.sql.Date.valueOf(date != null ? date : LocalDate.now()));
    }

    /**
     * The amount a purchase order counts against a budget: net + VAT x the supplier's factor, in the base currency.
     * Throws 409 NO_EXCHANGE_RATE when the PO is in a foreign currency with no rate, rather than guess.
     */
    public BigDecimal budgetAmountFor(UUID tenantId, UUID supplierId, String currency, LocalDate date,
                                      BigDecimal subtotal, BigDecimal total) {
        Policy co = forTenant(tenantId);
        Policy eff = new Policy(co.budgetBasis(), vatRecoverablePctFor(tenantId, supplierId), co.includeNonRecoverableVat(),
                co.priceIncreaseTolerancePct(), co.baseCurrency());
        BigDecimal local = budgetAmount(subtotal, total.subtract(subtotal), eff);
        if (ScmPolicyRules.isBase(currency, co.baseCurrency())) return local;
        BigDecimal rate = rateOrNull(tenantId, currency, date);
        if (rate == null)
            throw new HandyFlowException("There is no " + currency.toUpperCase(Locale.ROOT) + " to " + co.baseCurrency()
                    + " exchange rate on or before " + (date != null ? date : LocalDate.now())
                    + ". Add one under Procurement policy > Exchange rates.", HttpStatus.CONFLICT, "NO_EXCHANGE_RATE");
        return ScmPolicyRules.toBase(local, rate);
    }

    /**
     * SQL for the budget amount of a row of {@code alias} (columns subtotal, total_amount, supplier_id, currency,
     * tenant_id and {@code dateColumn}). Built from validated numbers and the tenant UUID only, never from user text.
     * A foreign-currency row with no rate counts at 1: the PO commit path refuses that case first.
     */
    public String amountSql(UUID tenantId, String alias, String dateColumn) {
        Policy p = forTenant(tenantId);
        String company = p.vatRecoverablePct().toPlainString();
        String pct = "COALESCE((SELECT sv.vat_recoverable_pct FROM sc_supplier_vat sv WHERE sv.tenant_id = '" + tenantId
                + "' AND sv.supplier_id = " + alias + ".supplier_id), " + company + ")";
        String factor = INC_VAT.equals(p.budgetBasis()) ? "1"
                : (NET_OF_RECOVERABLE.equals(p.budgetBasis()) || p.includeNonRecoverableVat()) ? "((100 - " + pct + ") / 100.0)" : "0";
        return "ROUND((" + alias + ".subtotal + (" + alias + ".total_amount - " + alias + ".subtotal) * " + factor + ") * COALESCE(sc_fx_rate("
                + alias + ".tenant_id, " + alias + ".currency, " + alias + "." + dateColumn + "), 1), 2)";
    }

    /**
     * Recomputes the amount of existing commitments under the current policy, optionally for one supplier's POs.
     * Commitments of foreign-currency POs with no rate are left alone. Returns {restated, skipped}.
     */
    @Transactional
    public int[] restateCommitments(UUID tenantId, UUID supplierId) {
        String supplierSql = supplierId != null ? " AND po.supplier_id = ?" : "";
        Object[] args = supplierId != null ? new Object[]{tenantId, supplierId} : new Object[]{tenantId};
        int restated = jdbc.update("UPDATE sc_budget_commitments c SET amount = " + amountSql(tenantId, "po", "order_date")
                + ", updated_at = now() FROM sc_purchase_orders po WHERE c.tenant_id = ? AND po.id = c.purchase_order_id "
                + "AND po.tenant_id = c.tenant_id AND sc_fx_rate(po.tenant_id, po.currency, po.order_date) IS NOT NULL" + supplierSql, args);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM sc_budget_commitments c JOIN sc_purchase_orders po ON po.id = c.purchase_order_id "
                + "WHERE c.tenant_id = ?" + supplierSql, Integer.class, args);
        return new int[]{restated, (total != null ? total : 0) - restated};
    }

    @Transactional
    public View save(UUID tenantId, Policy p, UUID userId, String userName) {
        String problem = problem(p);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "BAD_POLICY");
        Policy before = forTenant(tenantId);
        jdbc.update("""
                INSERT INTO sc_procurement_policy (tenant_id, budget_basis, vat_recoverable_pct, include_non_recoverable_vat,
                                                   price_increase_tolerance_pct, base_currency, updated_by, updated_by_name, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (tenant_id) DO UPDATE SET budget_basis = EXCLUDED.budget_basis,
                    vat_recoverable_pct = EXCLUDED.vat_recoverable_pct,
                    include_non_recoverable_vat = EXCLUDED.include_non_recoverable_vat,
                    price_increase_tolerance_pct = EXCLUDED.price_increase_tolerance_pct,
                    base_currency = EXCLUDED.base_currency,
                    updated_by = EXCLUDED.updated_by, updated_by_name = EXCLUDED.updated_by_name, updated_at = now()""",
                tenantId, p.budgetBasis(), p.vatRecoverablePct(), p.includeNonRecoverableVat(), p.priceIncreaseTolerancePct(),
                p.baseCurrency(), userId, userName);
        boolean moneyChanged = !before.budgetBasis().equals(p.budgetBasis())
                || before.vatRecoverablePct().compareTo(p.vatRecoverablePct()) != 0
                || before.includeNonRecoverableVat() != p.includeNonRecoverableVat()
                || !before.baseCurrency().equals(p.baseCurrency());
        int[] restated = moneyChanged ? restateCommitments(tenantId, null) : new int[]{0, 0};
        jdbc.update("""
                INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, before_json, after_json)
                VALUES (?, 'STAFF', ?, ?, 'SCM_PROCUREMENT_POLICY_CHANGED', 'ScProcurementPolicy', ?, ?::jsonb, ?::jsonb)""",
                tenantId, userId, userName, tenantId, json(before), json(p, restated));
        return view(tenantId);
    }

    private Optional<Policy> stored(UUID tenantId) {
        return jdbc.query("SELECT budget_basis, vat_recoverable_pct, include_non_recoverable_vat, price_increase_tolerance_pct, base_currency "
                        + "FROM sc_procurement_policy WHERE tenant_id = ?",
                (rs, n) -> new Policy(rs.getString("budget_basis"), rs.getBigDecimal("vat_recoverable_pct"),
                        rs.getBoolean("include_non_recoverable_vat"), rs.getBigDecimal("price_increase_tolerance_pct"),
                        rs.getString("base_currency")), tenantId)
                .stream().findFirst();
    }

    private static String json(Policy p) { return json(p, null); }

    private static String json(Policy p, int[] restated) {
        return "{\"budgetBasis\":\"" + p.budgetBasis() + "\",\"vatRecoverablePct\":" + p.vatRecoverablePct()
                + ",\"includeNonRecoverableVat\":" + p.includeNonRecoverableVat()
                + ",\"priceIncreaseTolerancePct\":" + p.priceIncreaseTolerancePct()
                + ",\"baseCurrency\":\"" + p.baseCurrency() + "\""
                + (restated != null ? ",\"commitmentsRestated\":" + restated[0] + ",\"commitmentsSkipped\":" + restated[1] : "") + "}";
    }
}
