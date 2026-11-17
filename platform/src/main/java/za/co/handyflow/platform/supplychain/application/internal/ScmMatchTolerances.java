package za.co.handyflow.platform.supplychain.application.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Matching tolerances and policy switches. Today these come from application config
 * (global); the 2% that used to be a hardcoded constant in ScmService is now just the
 * default. forTenant() is the seam where a per-tenant / per-category settings table
 * plugs in later without touching the engine or the service.
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

    private final Tolerances defaults;

    public ScmMatchTolerances(
            @Value("${app.scm.match.price-tolerance-pct:2.0}") BigDecimal pricePct,
            @Value("${app.scm.match.quantity-tolerance-pct:0.0}") BigDecimal quantityPct,
            @Value("${app.scm.match.amount-tolerance-pct:2.0}") BigDecimal amountPct,
            @Value("${app.scm.match.rounding-tolerance:0.10}") BigDecimal roundingAbs,
            @Value("${app.scm.match.allow-header-only:true}") boolean allowHeaderOnly,
            @Value("${app.scm.match.require-po:false}") boolean requirePo) {
        this.defaults = new Tolerances(pricePct, quantityPct, amountPct, roundingAbs, allowHeaderOnly, requirePo);
    }

    public Tolerances forTenant(UUID tenantId) {
        return defaults;
    }
}
