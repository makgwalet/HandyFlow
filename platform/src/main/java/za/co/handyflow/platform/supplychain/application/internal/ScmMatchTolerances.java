package za.co.handyflow.platform.supplychain.application.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Matching tolerances and policy switches. A company's own row in sc_match_tolerances wins; a company with no row gets
 * the application defaults below. Changing them is a recorded, validated action (see {@link #save}); it applies to
 * matches run afterwards and never rewrites an invoice that is already approved.
 *
 * <pre>
 * app.scm.match.price-tolerance-pct=2.0     # invoice unit price may exceed PO price by this %
 * app.scm.match.quantity-tolerance-pct=0.0  # invoiced qty may exceed accepted-and-uninvoiced qty by this %
 * app.scm.match.amount-tolerance-pct=2.0    # header-level total tolerance
 * app.scm.match.rounding-tolerance=0.10     # absolute rand tolerance for rounding differences
 * app.scm.match.allow-header-only=true      # accept invoices without lines (header-level match)
 * app.scm.match.require-po=false            # true = invoices without a PO are held
 * </pre>
 */
@Component
public class ScmMatchTolerances {

    public record Tolerances(BigDecimal pricePct, BigDecimal quantityPct, BigDecimal amountPct,
                             BigDecimal roundingAbs, boolean allowHeaderOnly, boolean requirePo) {}

    /** What the settings screen shows: the effective values and whether they are the company's own. */
    public record View(Tolerances effective, Tolerances defaults, boolean customised) {}

    private final Tolerances defaults;
    private final JdbcTemplate jdbc;

    public ScmMatchTolerances(
            JdbcTemplate jdbc,
            @Value("${app.scm.match.price-tolerance-pct:2.0}") BigDecimal pricePct,
            @Value("${app.scm.match.quantity-tolerance-pct:0.0}") BigDecimal quantityPct,
            @Value("${app.scm.match.amount-tolerance-pct:2.0}") BigDecimal amountPct,
            @Value("${app.scm.match.rounding-tolerance:0.10}") BigDecimal roundingAbs,
            @Value("${app.scm.match.allow-header-only:true}") boolean allowHeaderOnly,
            @Value("${app.scm.match.require-po:false}") boolean requirePo) {
        this.jdbc = jdbc;
        this.defaults = new Tolerances(pricePct, quantityPct, amountPct, roundingAbs, allowHeaderOnly, requirePo);
    }

    public Tolerances forTenant(UUID tenantId) {
        return stored(tenantId).orElse(defaults);
    }

    public View view(UUID tenantId) {
        Optional<Tolerances> own = stored(tenantId);
        return new View(own.orElse(defaults), defaults, own.isPresent());
    }

    /** Problem with the proposed values, or null when they are acceptable. Pure, so it is tested without Spring. */
    public static String problem(Tolerances t) {
        if (t == null || t.pricePct() == null || t.quantityPct() == null || t.amountPct() == null || t.roundingAbs() == null)
            return "All four tolerances are required";
        BigDecimal max = BigDecimal.valueOf(25);
        for (BigDecimal pct : new BigDecimal[]{t.pricePct(), t.quantityPct(), t.amountPct()}) {
            if (pct.signum() < 0 || pct.compareTo(max) > 0) return "A percentage tolerance must be between 0 and 25";
        }
        if (t.roundingAbs().signum() < 0 || t.roundingAbs().compareTo(BigDecimal.valueOf(100)) > 0)
            return "The rounding tolerance must be between 0 and 100";
        return null;
    }

    @Transactional
    public View save(UUID tenantId, Tolerances t, UUID userId, String userName) {
        String problem = problem(t);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "BAD_TOLERANCES");
        Tolerances before = forTenant(tenantId);
        jdbc.update("""
                INSERT INTO sc_match_tolerances (tenant_id, price_pct, quantity_pct, amount_pct, rounding_abs, allow_header_only,
                                                 require_po, updated_by, updated_by_name, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (tenant_id) DO UPDATE SET price_pct = EXCLUDED.price_pct, quantity_pct = EXCLUDED.quantity_pct,
                    amount_pct = EXCLUDED.amount_pct, rounding_abs = EXCLUDED.rounding_abs,
                    allow_header_only = EXCLUDED.allow_header_only, require_po = EXCLUDED.require_po,
                    updated_by = EXCLUDED.updated_by, updated_by_name = EXCLUDED.updated_by_name, updated_at = now()""",
                tenantId, t.pricePct(), t.quantityPct(), t.amountPct(), t.roundingAbs(), t.allowHeaderOnly(), t.requirePo(),
                userId, userName);
        jdbc.update("""
                INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, before_json, after_json)
                VALUES (?, 'STAFF', ?, ?, 'SCM_MATCH_TOLERANCES_CHANGED', 'ScMatchTolerances', ?, ?::jsonb, ?::jsonb)""",
                tenantId, userId, userName, tenantId, json(before), json(t));
        return view(tenantId);
    }

    private Optional<Tolerances> stored(UUID tenantId) {
        return jdbc.query("SELECT price_pct, quantity_pct, amount_pct, rounding_abs, allow_header_only, require_po "
                        + "FROM sc_match_tolerances WHERE tenant_id = ?",
                (rs, n) -> new Tolerances(rs.getBigDecimal("price_pct"), rs.getBigDecimal("quantity_pct"),
                        rs.getBigDecimal("amount_pct"), rs.getBigDecimal("rounding_abs"),
                        rs.getBoolean("allow_header_only"), rs.getBoolean("require_po")), tenantId)
                .stream().findFirst();
    }

    private static String json(Tolerances t) {
        return "{\"pricePct\":" + t.pricePct() + ",\"quantityPct\":" + t.quantityPct() + ",\"amountPct\":" + t.amountPct()
                + ",\"roundingAbs\":" + t.roundingAbs() + ",\"allowHeaderOnly\":" + t.allowHeaderOnly()
                + ",\"requirePo\":" + t.requirePo() + "}";
    }
}
