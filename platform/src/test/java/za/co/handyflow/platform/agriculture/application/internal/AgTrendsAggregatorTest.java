package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.agriculture.application.internal.AgTrendsAggregator.*;
import za.co.handyflow.platform.agriculture.dto.AgTrendsResponse;
import za.co.handyflow.platform.agriculture.dto.AgTrendsResponse.Comparison;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgTrendsAggregatorTest {

    // Thursday 15 October 2026. Windows: current 16 Sep to 15 Oct, previous 17 Aug to 15 Sep.
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final UUID GREEN = UUID.randomUUID(), RIVER = UUID.randomUUID();
    private static final UUID MAIZE = UUID.randomUUID(), BEANS = UUID.randomUUID(), GONE = UUID.randomUUID();
    private static final Map<UUID, CropInfo> CROPS = Map.of(MAIZE, new CropInfo("Maize", "t"), BEANS, new CropInfo("Dry beans", "bags"));

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static LocalDate d(String s) { return LocalDate.parse(s); }
    private static void assertNumber(String expected, BigDecimal actual) { assertEquals(0, bd(expected).compareTo(actual), "expected " + expected + " but was " + actual); }

    private static DatedAmount amt(String date, UUID farm, String amount) { return new DatedAmount(d(date), farm, bd(amount)); }
    private static HarvestRow harvest(String date, UUID farm, UUID crop, String qty, String unit) { return new HarvestRow(d(date), farm, crop, bd(qty), unit); }

    private static Rows rows(List<DatedAmount> seed, List<DatedAmount> inputs, List<DatedAmount> feed, List<DatedAmount> health, List<DatedAmount> purchases,
                             List<HarvestRow> harvests, List<EventRow> births, List<EventRow> deaths, List<ScoutingRow> scouting) {
        return new Rows(seed, inputs, feed, health, purchases, harvests, births, deaths, scouting);
    }
    private static Rows none() { return rows(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()); }
    private static Rows costs(List<DatedAmount> inputs, List<DatedAmount> feed) { return rows(List.of(), inputs, feed, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()); }
    private static Rows harvests(HarvestRow... h) { return rows(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(h), List.of(), List.of(), List.of()); }

    private static AgTrendsResponse build(int months, UUID farm, Rows r) { return AgTrendsAggregator.build(TODAY, months, farm, r, CROPS); }
    private static Comparison cmp(AgTrendsResponse r, String key) { return r.comparisons().stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow(); }

    @Test
    @DisplayName("the month axis runs oldest to newest and only the current month is partial, ending today")
    void monthAxis() {
        var r = build(3, null, none());
        assertEquals(List.of("2026-08", "2026-09", "2026-10"), r.months().stream().map(m -> m.key()).toList());
        assertEquals(d("2026-08-01"), r.months().get(0).start());
        assertEquals(d("2026-08-31"), r.months().get(0).end());
        assertFalse(r.months().get(1).partial());
        assertTrue(r.months().get(2).partial());
        assertEquals(TODAY, r.months().get(2).end());
        assertEquals(3, r.costs().size());
        assertEquals(3, r.production().tonnes().size());
        assertEquals(3, r.livestock().size());
    }

    @Test
    @DisplayName("the month axis wraps the year")
    void monthAxisWrapsYear() {
        var r = AgTrendsAggregator.build(d("2026-02-10"), 3, null, none(), CROPS);
        assertEquals(List.of("2025-12", "2026-01", "2026-02"), r.months().stream().map(m -> m.key()).toList());
        assertEquals(d("2026-02-10"), r.months().get(2).end());
    }

    @Test
    @DisplayName("months must be between 1 and 24")
    void monthsAreBounded() {
        assertThrows(IllegalArgumentException.class, () -> build(0, null, none()));
        assertThrows(IllegalArgumentException.class, () -> build(25, null, none()));
        assertDoesNotThrow(() -> build(1, null, none()));
        assertDoesNotThrow(() -> build(24, null, none()));
    }

    @Test
    @DisplayName("costs are bucketed by month and category, and the total is their sum")
    void costBuckets() {
        var r = build(3, null, rows(
                List.of(amt("2026-09-05", GREEN, "100")),                                         // seed
                List.of(amt("2026-09-20", GREEN, "250.50"), amt("2026-10-02", RIVER, "40")),     // inputs
                List.of(amt("2026-08-31", GREEN, "10"), amt("2026-09-01", GREEN, "20")),         // feed
                List.of(amt("2026-10-15", GREEN, "5")),                                           // health
                List.of(amt("2026-08-01", RIVER, "1000")),                                        // animal purchases
                List.of(), List.of(), List.of(), List.of()));
        var aug = r.costs().get(0); var sep = r.costs().get(1); var oct = r.costs().get(2);
        assertNumber("10", aug.feed()); assertNumber("1000", aug.animalPurchases()); assertNumber("1010", aug.total());
        assertNumber("100", sep.seed()); assertNumber("250.5", sep.inputs()); assertNumber("20", sep.feed()); assertNumber("370.5", sep.total());
        assertNumber("40", oct.inputs()); assertNumber("5", oct.health()); assertNumber("45", oct.total());
        assertEquals(2, sep.total().scale(), "money is reported to the cent");
    }

    @Test
    @DisplayName("entries before the first month or after today are ignored")
    void outOfRangeRowsAreIgnored() {
        var r = build(2, null, costs(List.of(amt("2026-08-31", GREEN, "999"), amt("2026-10-16", GREEN, "888"), amt("2027-01-01", GREEN, "777"), amt("2026-09-01", GREEN, "1")), List.of()));
        assertNumber("0", r.costs().get(0).total().subtract(bd("1")));                 // September has only the R1 entry
        assertNumber("0", r.costs().get(1).total());                                   // nothing in October: the 16th is tomorrow
        // the comparison windows are independent of the chart: 31 Aug is outside the 2-month chart but inside the previous window
        assertNumber("0", cmp(r, "TOTAL_COST").current());                             // the 16th onwards is tomorrow, the 1st is before 16 Sep
        assertNumber("1000", cmp(r, "TOTAL_COST").previous());                         // 999 on 31 Aug + 1 on 1 Sep
    }

    @Test
    @DisplayName("a farm filter counts only that farm's rows, everywhere")
    void farmFilter() {
        Rows rows = rows(List.of(), List.of(amt("2026-10-01", GREEN, "100"), amt("2026-10-01", RIVER, "30")), List.of(), List.of(), List.of(),
                List.of(harvest("2026-10-01", GREEN, MAIZE, "2", "t"), harvest("2026-10-01", RIVER, MAIZE, "9", "t")),
                List.of(new EventRow(d("2026-10-01"), RIVER, 4, null)), List.of(new EventRow(d("2026-10-01"), GREEN, 1, bd("500"))), List.of());
        var green = build(1, GREEN, rows);
        assertNumber("100", green.costs().get(0).total());
        assertNumber("2", green.production().tonnes().get(0).tonnes());
        assertEquals(0, green.livestock().get(0).births());
        assertEquals(1, green.livestock().get(0).deaths());
        assertEquals(GREEN, green.farmId());
        var all = build(1, null, rows);
        assertNumber("130", all.costs().get(0).total());
        assertNumber("11", all.production().tonnes().get(0).tonnes());
        assertEquals(4, all.livestock().get(0).births());
        assertNull(all.farmId());
    }

    @Test
    @DisplayName("tonnes convert kilograms and tonnes; a unit that is not a mass is left out and counted")
    void tonnesAndExclusions() {
        var r = build(2, null, harvests(
                harvest("2026-09-10", GREEN, MAIZE, "5", "t"), harvest("2026-09-12", GREEN, MAIZE, "800", "kg"),
                harvest("2026-10-02", GREEN, BEANS, "40", "bags"), harvest("2026-10-03", RIVER, MAIZE, "1500", "Kilograms")));
        assertNumber("5.8", r.production().tonnes().get(0).tonnes());
        assertNumber("1.5", r.production().tonnes().get(1).tonnes());                  // the bags are not a mass
        assertEquals(1, r.production().excludedRecords());
        assertTrue(r.limitations().stream().anyMatch(l -> l.contains("1 harvest record(s)") && l.contains("bags")));
        assertEquals(3, r.production().tonnes().get(0).tonnes().scale(), "tonnes are reported to three decimals");
    }

    @Test
    @DisplayName("each crop is reported in its own unit: tonnes crop converts, bag crop stays in bags, unknown crops keep their recorded unit")
    void byCrop() {
        var r = build(2, null, harvests(
                harvest("2026-09-10", GREEN, MAIZE, "5", "t"), harvest("2026-10-12", GREEN, MAIZE, "800", "kg"),
                harvest("2026-10-02", GREEN, BEANS, "40", "bags"), harvest("2026-10-04", GREEN, BEANS, "10", "bags"),
                harvest("2026-10-05", GREEN, GONE, "3", "crates")));
        assertEquals(List.of("Dry beans", "Maize", "Unknown crop"), r.production().byCrop().stream().map(c -> c.cropName()).toList());
        var beans = r.production().byCrop().get(0);
        assertEquals("bags", beans.unit()); assertNumber("0", beans.values().get(0)); assertNumber("50", beans.values().get(1)); assertNumber("50", beans.total());
        var maize = r.production().byCrop().get(1);
        assertEquals("t", maize.unit()); assertNumber("5", maize.values().get(0)); assertNumber("0.8", maize.values().get(1)); assertNumber("5.8", maize.total());
        var unknown = r.production().byCrop().get(2);
        assertEquals("crates", unknown.unit()); assertNumber("3", unknown.total());
    }

    @Test
    @DisplayName("a harvest that cannot convert to its crop's unit is left out of that crop and counted there")
    void cropLevelExclusions() {
        var r = build(1, null, harvests(harvest("2026-10-02", GREEN, MAIZE, "5", "t"), harvest("2026-10-03", GREEN, MAIZE, "40", "bags")));
        var maize = r.production().byCrop().get(0);
        assertNumber("5", maize.total());
        assertEquals(1, maize.excludedRecords());
    }

    @Test
    @DisplayName("births and deaths are counted per month, with the recorded value of losses")
    void livestockEvents() {
        var r = build(2, null, rows(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new EventRow(d("2026-09-03"), GREEN, 3, null), new EventRow(d("2026-10-01"), GREEN, 1, null)),
                List.of(new EventRow(d("2026-09-10"), GREEN, 5, bd("2500.50")), new EventRow(d("2026-09-11"), GREEN, 1, null), new EventRow(d("2026-10-02"), RIVER, 2, bd("100"))),
                List.of()));
        assertEquals(3, r.livestock().get(0).births()); assertEquals(6, r.livestock().get(0).deaths()); assertNumber("2500.5", r.livestock().get(0).estimatedLoss());
        assertEquals(1, r.livestock().get(1).births()); assertEquals(2, r.livestock().get(1).deaths()); assertNumber("100", r.livestock().get(1).estimatedLoss());
    }

    @Test
    @DisplayName("comparison windows: the last 30 days against the 30 before, with exact boundary days")
    void windowBoundaries() {
        var r = build(3, null, costs(List.of(
                amt("2026-09-16", GREEN, "100"),     // first day of the current window
                amt("2026-10-15", GREEN, "50"),      // today: current
                amt("2026-09-15", GREEN, "10"),      // last day of the previous window
                amt("2026-08-17", GREEN, "20"),      // first day of the previous window
                amt("2026-08-16", GREEN, "9999")),   // one day too early: in neither window
                List.of()));
        var total = cmp(r, "TOTAL_COST");
        assertNumber("150", total.current()); assertNumber("30", total.previous());
        assertEquals(d("2026-09-16"), total.currentFrom()); assertEquals(TODAY, total.currentTo());
        assertEquals(d("2026-08-17"), total.previousFrom()); assertEquals(d("2026-09-15"), total.previousTo());
        assertNumber("400", total.changePercent());                                    // (150 - 30) / 30
    }

    @Test
    @DisplayName("percentage change: up, down, and none when the earlier period was zero")
    void percentChange() {
        var up = build(3, null, costs(List.of(amt("2026-10-01", GREEN, "150"), amt("2026-09-01", GREEN, "100")), List.of()));
        assertNumber("50", cmp(up, "TOTAL_COST").changePercent());
        var down = build(3, null, costs(List.of(amt("2026-10-01", GREEN, "75"), amt("2026-09-01", GREEN, "100")), List.of()));
        assertNumber("-25", cmp(down, "TOTAL_COST").changePercent());
        var fresh = build(3, null, costs(List.of(amt("2026-10-01", GREEN, "75")), List.of()));
        assertNull(cmp(fresh, "TOTAL_COST").changePercent());
        assertNumber("75", cmp(fresh, "TOTAL_COST").current());
        var third = build(3, null, costs(List.of(amt("2026-10-01", GREEN, "200"), amt("2026-09-01", GREEN, "300")), List.of()));
        assertNumber("-33.3", cmp(third, "TOTAL_COST").changePercent());               // rounded to one decimal
    }

    @Test
    @DisplayName("total, crop and livestock cost split the categories the way the cost reports do")
    void costSplit() {
        var r = build(1, null, rows(List.of(amt("2026-10-01", GREEN, "1")), List.of(amt("2026-10-01", GREEN, "2")), List.of(amt("2026-10-01", GREEN, "4")),
                List.of(amt("2026-10-01", GREEN, "8")), List.of(amt("2026-10-01", GREEN, "16")), List.of(), List.of(), List.of(), List.of()));
        assertNumber("31", cmp(r, "TOTAL_COST").current());
        assertNumber("3", cmp(r, "CROP_COST").current());              // seed + inputs
        assertNumber("28", cmp(r, "LIVESTOCK_COST").current());        // feed + health + animal purchases
    }

    @Test
    @DisplayName("harvest, births, deaths and high-severity scouting are compared too; only HIGH scouting counts")
    void otherComparisons() {
        var r = build(3, null, rows(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(harvest("2026-10-01", GREEN, MAIZE, "6", "t"), harvest("2026-09-01", GREEN, MAIZE, "3000", "kg")),
                List.of(new EventRow(d("2026-10-01"), GREEN, 2, null)),
                List.of(new EventRow(d("2026-10-02"), GREEN, 1, null), new EventRow(d("2026-09-01"), GREEN, 4, null)),
                List.of(new ScoutingRow(d("2026-10-01"), GREEN, "HIGH"), new ScoutingRow(d("2026-10-02"), GREEN, "LOW"), new ScoutingRow(d("2026-09-01"), GREEN, "HIGH"), new ScoutingRow(d("2026-09-02"), GREEN, "HIGH"))));
        assertNumber("6", cmp(r, "HARVEST_TONNES").current()); assertNumber("3", cmp(r, "HARVEST_TONNES").previous()); assertNumber("100", cmp(r, "HARVEST_TONNES").changePercent());
        assertNumber("2", cmp(r, "BIRTHS").current()); assertNull(cmp(r, "BIRTHS").changePercent());
        assertNumber("1", cmp(r, "DEATHS").current()); assertNumber("4", cmp(r, "DEATHS").previous()); assertNumber("-75", cmp(r, "DEATHS").changePercent());
        assertNumber("1", cmp(r, "HIGH_SEVERITY_SCOUTING").current()); assertNumber("2", cmp(r, "HIGH_SEVERITY_SCOUTING").previous());
        assertEquals("head", cmp(r, "DEATHS").unit()); assertEquals("t", cmp(r, "HARVEST_TONNES").unit()); assertEquals("R", cmp(r, "TOTAL_COST").unit());
    }

    @Test
    @DisplayName("comparisons work even when the chart covers only the current month")
    void comparisonsIndependentOfChartLength() {
        var r = build(1, null, costs(List.of(amt("2026-09-20", GREEN, "100"), amt("2026-08-20", GREEN, "50")), List.of()));
        assertNumber("100", cmp(r, "TOTAL_COST").current());
        assertNumber("50", cmp(r, "TOTAL_COST").previous());
        assertNumber("0", r.costs().get(0).total());                                   // the chart's only month is October
    }

    @Test
    @DisplayName("earliestNeeded covers the previous window when the chart is short, and the first month when it is long")
    void earliestNeeded() {
        assertEquals(d("2026-08-17"), AgTrendsAggregator.earliestNeeded(TODAY, 1));
        assertEquals(d("2026-08-17"), AgTrendsAggregator.earliestNeeded(TODAY, 2));
        assertEquals(d("2025-11-01"), AgTrendsAggregator.earliestNeeded(TODAY, 12));
    }

    @Test
    @DisplayName("no data gives a well-formed response with zeros, no percentages, and the standing limitations")
    void emptyIsWellFormed() {
        var r = build(2, null, none());
        assertEquals(7, r.comparisons().size());
        for (Comparison c : r.comparisons()) { assertNumber("0", c.current()); assertNull(c.changePercent(), c.key()); }
        assertTrue(r.production().byCrop().isEmpty());
        assertEquals(0, r.production().excludedRecords());
        assertEquals(2, r.limitations().size());
        assertTrue(r.limitations().get(0).contains("Herd size"));
        assertTrue(r.limitations().get(1).contains("recorded costs only") && r.limitations().get(1).contains("Profitability") && r.limitations().get(1).contains("finance access"), r.limitations().get(1));
        assertFalse(r.limitations().get(1).contains("are not recorded"), "labour, equipment and revenue ARE recorded now (W2 to W4); the text must not say otherwise");
        assertEquals(TODAY, r.asOf());
    }
}
