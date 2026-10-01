package za.co.handyflow.platform.agriculture.application.internal;

import za.co.handyflow.platform.agriculture.domain.rules.AgUnits;
import za.co.handyflow.platform.agriculture.dto.AgTrendsResponse;
import za.co.handyflow.platform.agriculture.dto.AgTrendsResponse.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Buckets dated rows into months and 30-day comparison windows. Pure (no Spring, no repositories), so the date and unit
 * arithmetic is unit tested directly and {@link AgTrendsService} only fetches rows and works out which farm each belongs to.
 * <p>
 * Rows arrive already attributed to a farm. With a farm filter only that farm's rows count; without one everything counts.
 * Anything dated after {@code asOf} (a future-dated entry) or before the first month is ignored.
 */
public final class AgTrendsAggregator {

    private AgTrendsAggregator() {}

    public static final int MIN_MONTHS = 1;
    public static final int MAX_MONTHS = 24;
    /** Length of each comparison window, in days. */
    public static final int WINDOW_DAYS = 30;

    public record DatedAmount(LocalDate date, UUID farmId, BigDecimal amount) {}
    public record HarvestRow(LocalDate date, UUID farmId, UUID cropTypeId, BigDecimal quantity, String unit) {}
    /** A birth or death event: {@code count} is head, {@code value} an optional recorded money value. */
    public record EventRow(LocalDate date, UUID farmId, long count, BigDecimal value) {}
    public record ScoutingRow(LocalDate date, UUID farmId, String severity) {}
    public record CropInfo(String name, String unit) {}

    /** All the rows to aggregate, grouped so {@link #build} stays readable. */
    public record Rows(List<DatedAmount> seed, List<DatedAmount> inputs, List<DatedAmount> feed, List<DatedAmount> health,
                       List<DatedAmount> purchases, List<HarvestRow> harvests, List<EventRow> births, List<EventRow> deaths,
                       List<ScoutingRow> scouting) {}

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static boolean mine(UUID filter, UUID farmId) { return filter == null || filter.equals(farmId); }

    private static boolean within(LocalDate d, LocalDate from, LocalDate to) {
        return d != null && !d.isBefore(from) && !d.isAfter(to);
    }

    /** The earliest date the rows need to cover: the first month, or the start of the previous comparison window if that is earlier. */
    public static LocalDate earliestNeeded(LocalDate asOf, int months) {
        LocalDate firstMonth = YearMonth.from(asOf).minusMonths(months - 1L).atDay(1);
        LocalDate previousWindowStart = asOf.minusDays(2L * WINDOW_DAYS - 1);
        return previousWindowStart.isBefore(firstMonth) ? previousWindowStart : firstMonth;
    }

    public static AgTrendsResponse build(LocalDate asOf, int monthCount, UUID farmFilter, Rows rows, Map<UUID, CropInfo> crops) {
        if (monthCount < MIN_MONTHS || monthCount > MAX_MONTHS) {
            throw new IllegalArgumentException("months must be between " + MIN_MONTHS + " and " + MAX_MONTHS);
        }
        YearMonth current = YearMonth.from(asOf);
        List<Month> months = new ArrayList<>();
        for (int i = monthCount - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            boolean partial = ym.equals(current);
            months.add(new Month(ym.toString(), ym.atDay(1), partial ? asOf : ym.atEndOfMonth(), partial));
        }
        LocalDate from = months.get(0).start();
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < months.size(); i++) index.put(months.get(i).key(), i);
        java.util.function.Function<LocalDate, Integer> slot = d -> within(d, from, asOf) ? index.get(YearMonth.from(d).toString()) : null;

        // ---- costs ------------------------------------------------------------------------------------------------
        BigDecimal[][] cost = new BigDecimal[5][monthCount];            // seed, inputs, feed, health, purchases
        for (BigDecimal[] row : cost) java.util.Arrays.fill(row, BigDecimal.ZERO);
        List<List<DatedAmount>> sources = List.of(rows.seed(), rows.inputs(), rows.feed(), rows.health(), rows.purchases());
        for (int c = 0; c < sources.size(); c++) {
            for (DatedAmount r : sources.get(c)) {
                Integer i = slot.apply(r.date());
                if (i != null && mine(farmFilter, r.farmId())) cost[c][i] = cost[c][i].add(nz(r.amount()));
            }
        }
        List<CostMonth> costs = new ArrayList<>();
        for (int i = 0; i < monthCount; i++) {
            BigDecimal total = cost[0][i].add(cost[1][i]).add(cost[2][i]).add(cost[3][i]).add(cost[4][i]);
            costs.add(new CostMonth(months.get(i).key(), money(cost[0][i]), money(cost[1][i]), money(cost[2][i]), money(cost[3][i]), money(cost[4][i]), money(total)));
        }

        // ---- production -------------------------------------------------------------------------------------------
        BigDecimal[] tonnes = new BigDecimal[monthCount];
        java.util.Arrays.fill(tonnes, BigDecimal.ZERO);
        int excluded = 0;
        Map<UUID, BigDecimal[]> cropValues = new LinkedHashMap<>();
        Map<UUID, Integer> cropExcluded = new HashMap<>();
        Map<UUID, String> cropUnit = new HashMap<>();
        for (HarvestRow h : rows.harvests()) {
            Integer i = slot.apply(h.date());
            if (i == null || !mine(farmFilter, h.farmId()) || h.quantity() == null) continue;
            var asTonnes = AgUnits.convert(h.quantity(), h.unit(), "t");
            if (asTonnes.isPresent()) tonnes[i] = tonnes[i].add(asTonnes.get()); else excluded++;

            CropInfo info = crops.get(h.cropTypeId());
            String unit = cropUnit.computeIfAbsent(h.cropTypeId(), k -> info != null && info.unit() != null && !info.unit().isBlank() ? info.unit() : h.unit());
            BigDecimal[] values = cropValues.computeIfAbsent(h.cropTypeId(), k -> {
                BigDecimal[] z = new BigDecimal[monthCount];
                java.util.Arrays.fill(z, BigDecimal.ZERO);
                return z;
            });
            var inCropUnit = AgUnits.convert(h.quantity(), h.unit(), unit);
            if (inCropUnit.isPresent()) values[i] = values[i].add(inCropUnit.get()); else cropExcluded.merge(h.cropTypeId(), 1, Integer::sum);
        }
        List<TonnesMonth> tonnesMonths = new ArrayList<>();
        for (int i = 0; i < monthCount; i++) tonnesMonths.add(new TonnesMonth(months.get(i).key(), tonnes[i].setScale(3, RoundingMode.HALF_UP)));
        List<CropSeries> byCrop = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal[]> e : cropValues.entrySet()) {
            CropInfo info = crops.get(e.getKey());
            List<BigDecimal> values = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (BigDecimal v : e.getValue()) { values.add(v.setScale(3, RoundingMode.HALF_UP)); total = total.add(v); }
            byCrop.add(new CropSeries(e.getKey(), info != null ? info.name() : "Unknown crop", cropUnit.get(e.getKey()), values,
                    total.setScale(3, RoundingMode.HALF_UP), cropExcluded.getOrDefault(e.getKey(), 0)));
        }
        byCrop.sort(Comparator.comparing(CropSeries::cropName, String.CASE_INSENSITIVE_ORDER));

        // ---- livestock events -------------------------------------------------------------------------------------
        long[] births = new long[monthCount], deaths = new long[monthCount];
        BigDecimal[] loss = new BigDecimal[monthCount];
        java.util.Arrays.fill(loss, BigDecimal.ZERO);
        for (EventRow b : rows.births()) { Integer i = slot.apply(b.date()); if (i != null && mine(farmFilter, b.farmId())) births[i] += b.count(); }
        for (EventRow d : rows.deaths()) {
            Integer i = slot.apply(d.date());
            if (i != null && mine(farmFilter, d.farmId())) { deaths[i] += d.count(); loss[i] = loss[i].add(nz(d.value())); }
        }
        List<LivestockMonth> livestock = new ArrayList<>();
        for (int i = 0; i < monthCount; i++) livestock.add(new LivestockMonth(months.get(i).key(), births[i], deaths[i], money(loss[i])));

        // ---- last 30 days against the 30 before -------------------------------------------------------------------
        LocalDate curTo = asOf, curFrom = asOf.minusDays(WINDOW_DAYS - 1L);
        LocalDate prevTo = curFrom.minusDays(1), prevFrom = prevTo.minusDays(WINDOW_DAYS - 1L);
        List<Comparison> comparisons = new ArrayList<>();
        BigDecimal[] costNow = new BigDecimal[5], costBefore = new BigDecimal[5];
        for (int c = 0; c < 5; c++) {
            costNow[c] = windowSum(sources.get(c), farmFilter, curFrom, curTo);
            costBefore[c] = windowSum(sources.get(c), farmFilter, prevFrom, prevTo);
        }
        comparisons.add(compare("TOTAL_COST", "Total cost", "R", sum(costNow, 0, 5), sum(costBefore, 0, 5), curFrom, curTo, prevFrom, prevTo, 2));
        comparisons.add(compare("CROP_COST", "Crop cost", "R", sum(costNow, 0, 2), sum(costBefore, 0, 2), curFrom, curTo, prevFrom, prevTo, 2));
        comparisons.add(compare("LIVESTOCK_COST", "Livestock cost", "R", sum(costNow, 2, 5), sum(costBefore, 2, 5), curFrom, curTo, prevFrom, prevTo, 2));
        comparisons.add(compare("HARVEST_TONNES", "Harvested", "t", harvestTonnes(rows.harvests(), farmFilter, curFrom, curTo), harvestTonnes(rows.harvests(), farmFilter, prevFrom, prevTo), curFrom, curTo, prevFrom, prevTo, 3));
        comparisons.add(compare("BIRTHS", "Births", "head", eventSum(rows.births(), farmFilter, curFrom, curTo), eventSum(rows.births(), farmFilter, prevFrom, prevTo), curFrom, curTo, prevFrom, prevTo, 0));
        comparisons.add(compare("DEATHS", "Deaths", "head", eventSum(rows.deaths(), farmFilter, curFrom, curTo), eventSum(rows.deaths(), farmFilter, prevFrom, prevTo), curFrom, curTo, prevFrom, prevTo, 0));
        comparisons.add(compare("HIGH_SEVERITY_SCOUTING", "High-severity scouting findings", "findings", scoutingHigh(rows.scouting(), farmFilter, curFrom, curTo), scoutingHigh(rows.scouting(), farmFilter, prevFrom, prevTo), curFrom, curTo, prevFrom, prevTo, 0));

        List<String> limitations = new ArrayList<>();
        limitations.add("Herd size over time is not recorded, so livestock trends show births and deaths, not the number of animals.");
        limitations.add("Revenue, labour cost and equipment cost are not recorded, so there is no margin trend.");
        if (excluded > 0) {
            limitations.add(excluded + " harvest record(s) are in a unit that is not a mass (for example bags), so they are not in the tonnes chart; they still appear under their own crop.");
        }
        return new AgTrendsResponse(asOf, farmFilter, List.copyOf(months), List.copyOf(costs),
                new Production(List.copyOf(tonnesMonths), List.copyOf(byCrop), excluded), List.copyOf(livestock), List.copyOf(comparisons), List.copyOf(limitations));
    }

    private static BigDecimal money(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    private static BigDecimal sum(BigDecimal[] a, int from, int to) {
        BigDecimal t = BigDecimal.ZERO;
        for (int i = from; i < to; i++) t = t.add(a[i]);
        return t;
    }

    private static BigDecimal windowSum(List<DatedAmount> rows, UUID farm, LocalDate from, LocalDate to) {
        BigDecimal t = BigDecimal.ZERO;
        for (DatedAmount r : rows) if (within(r.date(), from, to) && mine(farm, r.farmId())) t = t.add(nz(r.amount()));
        return t;
    }

    private static BigDecimal harvestTonnes(List<HarvestRow> rows, UUID farm, LocalDate from, LocalDate to) {
        BigDecimal t = BigDecimal.ZERO;
        for (HarvestRow h : rows) {
            if (!within(h.date(), from, to) || !mine(farm, h.farmId()) || h.quantity() == null) continue;
            t = t.add(AgUnits.convert(h.quantity(), h.unit(), "t").orElse(BigDecimal.ZERO));
        }
        return t;
    }

    private static BigDecimal eventSum(List<EventRow> rows, UUID farm, LocalDate from, LocalDate to) {
        long t = 0;
        for (EventRow e : rows) if (within(e.date(), from, to) && mine(farm, e.farmId())) t += e.count();
        return BigDecimal.valueOf(t);
    }

    private static BigDecimal scoutingHigh(List<ScoutingRow> rows, UUID farm, LocalDate from, LocalDate to) {
        long t = rows.stream().filter(r -> within(r.date(), from, to) && mine(farm, r.farmId()) && "HIGH".equals(r.severity())).count();
        return BigDecimal.valueOf(t);
    }

    private static Comparison compare(String key, String label, String unit, BigDecimal now, BigDecimal before,
                                      LocalDate cf, LocalDate ct, LocalDate pf, LocalDate pt, int scale) {
        BigDecimal pct = before.signum() == 0 ? null
                : now.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before, 1, RoundingMode.HALF_UP);
        return new Comparison(key, label, unit, now.setScale(scale, RoundingMode.HALF_UP), before.setScale(scale, RoundingMode.HALF_UP), pct, cf, ct, pf, pt);
    }
}
