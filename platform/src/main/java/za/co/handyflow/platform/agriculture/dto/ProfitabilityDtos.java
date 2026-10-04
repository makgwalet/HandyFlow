package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** The gross-margin report for a farm (ADR-001, W5): revenue (ex-VAT, net of credit notes) minus DIRECT production costs, unit by unit. */
public final class ProfitabilityDtos {

    private ProfitabilityDtos() {}

    /**
     * One production unit: a crop cycle, a group (batch), an animal or an enterprise.
     *
     * @param state        COMPLETE when the unit's life is over (a harvested cycle, a closed group, a sold animal), so its margin is final;
     *                     IN_PROGRESS when it is still running, so the margin is only what has happened to date and unsold stock is not valued
     * @param recordedCost the costs already recorded where they happen: feed, health, seed, inputs and an animal's purchase price
     * @param labour       the cost-ledger categories (W3 to W4 and manual): labour, equipment and fuel; {@code otherDirect} is everything else in the ledger
     * @param grossMargin  revenue minus direct cost; never net profit (overheads, finance cost, depreciation and tax belong to Accounting)
     * @param marginPercent grossMargin as a percentage of revenue, to one decimal; null when there is no revenue
     * @param caveats      plain-language reasons this unit's margin should be read with care
     */
    public record UnitProfit(String targetType, UUID targetId, String label, String status, String state,
                             BigDecimal revenue, BigDecimal recordedCost, BigDecimal labour, BigDecimal equipment, BigDecimal fuel, BigDecimal otherDirect,
                             BigDecimal directCost, BigDecimal grossMargin, BigDecimal marginPercent, List<String> caveats) {}

    /** Totals over every unit, with the cost breakdown. Always the exact sum of the unit rows. */
    public record Totals(BigDecimal revenue, BigDecimal recordedCost, BigDecimal labour, BigDecimal equipment, BigDecimal fuel, BigDecimal otherDirect,
                         BigDecimal directCost, BigDecimal grossMargin, BigDecimal marginPercent) {}

    /** Totals over the units in one state. */
    public record Subtotal(int units, BigDecimal revenue, BigDecimal directCost, BigDecimal grossMargin, BigDecimal marginPercent) {}

    /**
     * @param complete   units whose life is over: their margin is final
     * @param inProgress units still running: costs to date against revenue to date; unsold stock is not valued
     * @param notes      farm-level reasons the report may be incomplete (labour not yet costed, sales not counted)
     */
    public record ProfitabilityResponse(UUID farmId, Totals totals, Subtotal complete, Subtotal inProgress, List<UnitProfit> units, List<String> notes) {}
}
