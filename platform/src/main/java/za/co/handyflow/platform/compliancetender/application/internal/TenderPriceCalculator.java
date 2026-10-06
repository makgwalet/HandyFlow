package za.co.handyflow.platform.compliancetender.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure tender price arithmetic (ADR-004): no repositories, no Spring, so it is tested without a database.
 * <p>
 * Order of calculation, every amount rounded to cents (HALF_UP) at the step it is produced, so what is shown always adds up:
 * <pre>
 *   line total   = quantity x unit cost
 *   direct cost  = sum of line totals
 *   overhead     = direct cost x overhead %
 *   contingency  = direct cost x contingency %
 *   profit       = (direct cost + overhead + contingency) x profit %
 *   price ex VAT = direct cost + overhead + contingency + profit
 *   VAT          = price ex VAT x VAT rate (zero when VAT does not apply)
 *   price incl.  = price ex VAT + VAT
 * </pre>
 * Margin is stated as profit / price ex VAT (what share of the price is profit), not as a markup on cost; the settings use the markup form because that is how estimators price.
 */
public final class TenderPriceCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private TenderPriceCalculator() {}

    public record Line(String section, BigDecimal quantity, BigDecimal unitCost) {}

    public record Settings(BigDecimal overheadPct, BigDecimal contingencyPct, BigDecimal profitPct,
                           boolean vatApplies, BigDecimal vatRatePct) {}

    public record SectionTotal(String section, int lineCount, BigDecimal subtotal) {}

    public record Breakdown(BigDecimal directCost, BigDecimal overhead, BigDecimal contingency, BigDecimal profit,
                            BigDecimal priceExVat, BigDecimal vat, BigDecimal priceInclVat,
                            BigDecimal marginPct, List<SectionTotal> sections) {}

    /** The money value of one line: quantity x unit cost, to cents. Null quantity or cost counts as zero. */
    public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitCost) {
        if (quantity == null || unitCost == null) return money(BigDecimal.ZERO);
        return money(quantity.multiply(unitCost));
    }

    public static Breakdown calculate(List<Line> lines, Settings settings) {
        if (settings == null) throw new IllegalArgumentException("settings are required");
        Map<String, BigDecimal> subtotals = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        BigDecimal direct = money(BigDecimal.ZERO);
        for (Line l : lines == null ? List.<Line>of() : lines) {
            BigDecimal total = lineTotal(l.quantity(), l.unitCost());
            String key = l.section() == null ? "" : l.section();
            subtotals.merge(key, total, BigDecimal::add);
            counts.merge(key, 1, Integer::sum);
            direct = direct.add(total);
        }
        BigDecimal overhead = percentOf(direct, settings.overheadPct());
        BigDecimal contingency = percentOf(direct, settings.contingencyPct());
        BigDecimal profit = percentOf(direct.add(overhead).add(contingency), settings.profitPct());
        BigDecimal exVat = direct.add(overhead).add(contingency).add(profit);
        BigDecimal vat = settings.vatApplies() ? percentOf(exVat, settings.vatRatePct()) : money(BigDecimal.ZERO);
        BigDecimal margin = exVat.signum() == 0 ? null
                : profit.multiply(HUNDRED).divide(exVat, 2, RoundingMode.HALF_UP);

        List<SectionTotal> sections = new ArrayList<>();
        subtotals.forEach((name, sub) -> sections.add(new SectionTotal(name, counts.get(name), sub)));
        return new Breakdown(direct, overhead, contingency, profit, exVat, vat, exVat.add(vat), margin, sections);
    }

    private static BigDecimal percentOf(BigDecimal base, BigDecimal pct) {
        if (pct == null) return money(BigDecimal.ZERO);
        return money(base.multiply(pct).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
