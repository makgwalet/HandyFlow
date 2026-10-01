package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Month-by-month trends and 30-day comparisons, computed from the dated records Agriculture already keeps (costs, harvests,
 * births, deaths, scouting). Every list in {@code costs}, {@code production.tonnes}, {@code production.byCrop[].values} and
 * {@code livestock} lines up index-for-index with {@code months}, oldest first; the last month is the current, partial one.
 * <p>
 * What it cannot say: Agriculture stores no head-count history (an animal's status changes, but not when), so livestock shows
 * the EVENTS (births, deaths), not herd size over time. There is also no revenue, labour-cost or equipment-cost data. See
 * {@code limitations}.
 */
public record AgTrendsResponse(
        LocalDate asOf,
        UUID farmId,                       // null when the figures cover every farm
        List<Month> months,
        List<CostMonth> costs,
        Production production,
        List<LivestockMonth> livestock,
        List<Comparison> comparisons,
        List<String> limitations
) {
    /** {@code key} is yyyy-MM. The current month is {@code partial}: its {@code end} is today, not month end. */
    public record Month(String key, LocalDate start, LocalDate end, boolean partial) {}

    /** The same cost definitions the cost reports use, so monthly totals add up to those reports. */
    public record CostMonth(String month, BigDecimal seed, BigDecimal inputs, BigDecimal feed, BigDecimal health,
                            BigDecimal animalPurchases, BigDecimal total) {}

    /**
     * @param tonnes          all harvests whose unit is a mass (kg, g, t, lb), converted to tonnes
     * @param byCrop          each crop in ITS OWN unit (a bag crop stays in bags), so crops are never added across units
     * @param excludedRecords harvest records left out of {@code tonnes} because their unit is not a mass (bags, bales...)
     */
    public record Production(List<TonnesMonth> tonnes, List<CropSeries> byCrop, int excludedRecords) {}

    public record TonnesMonth(String month, BigDecimal tonnes) {}

    /** {@code excludedRecords}: harvests of this crop whose unit cannot convert to {@code unit} (older data). */
    public record CropSeries(UUID cropTypeId, String cropName, String unit, List<BigDecimal> values, BigDecimal total, int excludedRecords) {}

    /** Head born and head lost that month. {@code estimatedLoss} is the recorded value of the loss, where one was entered. */
    public record LivestockMonth(String month, long births, long deaths, BigDecimal estimatedLoss) {}

    /**
     * The last 30 days against the 30 days before them: like-for-like, unlike a part-month against a full one.
     * {@code changePercent} is null when the earlier period was zero (no meaningful percentage).
     */
    public record Comparison(String key, String label, String unit, BigDecimal current, BigDecimal previous, BigDecimal changePercent,
                             LocalDate currentFrom, LocalDate currentTo, LocalDate previousFrom, LocalDate previousTo) {}
}
