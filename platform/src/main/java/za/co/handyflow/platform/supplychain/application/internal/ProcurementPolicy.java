package za.co.handyflow.platform.supplychain.application.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Existing commitments are not restated when the basis changes: pick the basis before go-live, not mid-year.
 */
@Component
public class ProcurementPolicy {

    public static final String EX_VAT = "EX_VAT", INC_VAT = "INC_VAT", NET_OF_RECOVERABLE = "NET_OF_RECOVERABLE";

    public record Policy(String budgetBasis, BigDecimal vatRecoverablePct, boolean includeNonRecoverableVat,
                         BigDecimal priceIncreaseTolerancePct) {}

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
        BigDecimal unrecoverable = BigDecimal.valueOf(100).subtract(p.vatRecoverablePct())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return switch (p.budgetBasis()) {
            case INC_VAT -> BigDecimal.ONE.setScale(4);
            case NET_OF_RECOVERABLE -> unrecoverable;
            default -> p.includeNonRecoverableVat() ? unrecoverable : BigDecimal.ZERO.setScale(4);
        };
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
        return null;
    }

    @Transactional
    public View save(UUID tenantId, Policy p, UUID userId, String userName) {
        String problem = problem(p);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "BAD_POLICY");
        Policy before = forTenant(tenantId);
        jdbc.update("""
                INSERT INTO sc_procurement_policy (tenant_id, budget_basis, vat_recoverable_pct, include_non_recoverable_vat,
                                                   price_increase_tolerance_pct, updated_by, updated_by_name, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (tenant_id) DO UPDATE SET budget_basis = EXCLUDED.budget_basis,
                    vat_recoverable_pct = EXCLUDED.vat_recoverable_pct,
                    include_non_recoverable_vat = EXCLUDED.include_non_recoverable_vat,
                    price_increase_tolerance_pct = EXCLUDED.price_increase_tolerance_pct,
                    updated_by = EXCLUDED.updated_by, updated_by_name = EXCLUDED.updated_by_name, updated_at = now()""",
                tenantId, p.budgetBasis(), p.vatRecoverablePct(), p.includeNonRecoverableVat(), p.priceIncreaseTolerancePct(),
                userId, userName);
        jdbc.update("""
                INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, before_json, after_json)
                VALUES (?, 'STAFF', ?, ?, 'SCM_PROCUREMENT_POLICY_CHANGED', 'ScProcurementPolicy', ?, ?::jsonb, ?::jsonb)""",
                tenantId, userId, userName, tenantId, json(before), json(p));
        return view(tenantId);
    }

    private Optional<Policy> stored(UUID tenantId) {
        return jdbc.query("SELECT budget_basis, vat_recoverable_pct, include_non_recoverable_vat, price_increase_tolerance_pct "
                        + "FROM sc_procurement_policy WHERE tenant_id = ?",
                (rs, n) -> new Policy(rs.getString("budget_basis"), rs.getBigDecimal("vat_recoverable_pct"),
                        rs.getBoolean("include_non_recoverable_vat"), rs.getBigDecimal("price_increase_tolerance_pct")), tenantId)
                .stream().findFirst();
    }

    private static String json(Policy p) {
        return "{\"budgetBasis\":\"" + p.budgetBasis() + "\",\"vatRecoverablePct\":" + p.vatRecoverablePct()
                + ",\"includeNonRecoverableVat\":" + p.includeNonRecoverableVat()
                + ",\"priceIncreaseTolerancePct\":" + p.priceIncreaseTolerancePct() + "}";
    }
}
