package za.co.handyflow.platform.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single source of truth for South Africa's standard VAT rate.
 * <p>
 * FIX (VAT consolidation pass): before this class existed, the current
 * 15% rate was hardcoded independently in at least four places, each
 * with its own literal and its own representation —
 * {@code AdminInvoiceService.VAT_RATE} (fraction, {@code 0.15}),
 * {@code PosService.VAT_RATE_STANDARD} (percentage, {@code 15}), and
 * {@code CatalogueService.createItem()}/{@code updateItem()} (percentage,
 * {@code "15.00"}, duplicated within that one file). Confirmed directly
 * by reading each file, not assumed. Changing the rate would have meant
 * finding and updating every one of those independently, with no
 * guarantee all of them were actually found.
 * <p>
 * Deliberately NOT a database-backed historical-rate table (the
 * {@code sars_tax_tables}/{@code sars_tax_rebates} pattern the {@code hr}/
 * {@code payrollbureau} modules already use for PAYE, which tracks a
 * genuinely different rate per tax year) — VAT in South Africa has
 * changed once in over a decade (14% → 15%, April 2018) and this
 * platform has no requirement yet to reproduce a historical invoice at
 * the rate that applied when it was originally issued (unlike payroll,
 * where a specific tax year's table is a hard legal requirement every
 * time). A single configurable value is the right-sized fix for what
 * was actually broken (scattered literals), not a speculative expansion
 * into rate history this pass has no evidence is needed. If that
 * changes, this is the one place a real rate-history table would plug
 * in behind the same two accessor methods below, without every caller
 * needing to change again.
 * <p>
 * Exposes both representations every existing call site already used,
 * rather than picking one and forcing every caller to convert — that
 * would have meant touching arithmetic this pass has no reason to
 * touch, beyond swapping the literal for a call to this class.
 */
@Component
public class VatRateProvider {

    private final BigDecimal ratePercent;

    public VatRateProvider(@Value("${app.tax.vat-rate-pct:15.00}") BigDecimal ratePercent) {
        this.ratePercent = ratePercent;
    }

    /** The VAT rate as a percentage, e.g. {@code 15.00} meaning 15%. */
    public BigDecimal ratePercent() {
        return ratePercent;
    }

    /** The VAT rate as a decimal fraction, e.g. {@code 0.15}. */
    public BigDecimal rateFraction() {
        return ratePercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }
}
