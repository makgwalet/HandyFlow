package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.agriculture.application.internal.AgProfitabilityAggregator.LedgerRow;
import za.co.handyflow.platform.agriculture.application.internal.AgProfitabilityAggregator.RevenueRow;
import za.co.handyflow.platform.agriculture.application.internal.AgProfitabilityAggregator.UnitInput;
import za.co.handyflow.platform.agriculture.application.internal.AgProfitabilityOverviewAggregator.FarmReport;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.FarmMargin;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.ProfitabilityOverviewResponse;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.ProfitabilityResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgProfitabilityOverviewAggregatorTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static void num(String expected, BigDecimal actual) { assertEquals(0, bd(expected).compareTo(actual), "expected " + expected + " but was " + actual); }

    /** A real per-farm report, built the way the service builds it: one harvested cycle with the given recorded cost and revenue. */
    private static FarmReport farm(String name, String recorded, String revenue) { return farm(name, recorded, revenue, 0, List.of()); }

    private static FarmReport farm(String name, String recorded, String revenue, int uncostedLabour, List<String> extra) {
        UUID farmId = UUID.randomUUID(), cycle = UUID.randomUUID();
        ProfitabilityResponse r = AgProfitabilityAggregator.build(farmId, List.of(new UnitInput("CROP_CYCLE", cycle, name + " maize", "HARVESTED", null, bd(recorded))),
                List.of(), List.of(new RevenueRow("CROP_CYCLE", cycle, bd(revenue))), uncostedLabour, 0, extra);
        return new FarmReport(farmId, name, r);
    }

    private static ProfitabilityOverviewResponse overview(FarmReport... farms) { return AgProfitabilityOverviewAggregator.build(List.of(farms), List.of()); }

    @Test
    @DisplayName("the overview totals are the exact sums of each farm's own totals, category by category")
    void totalsAreSums() {
        UUID f1 = UUID.randomUUID(), c1 = UUID.randomUUID(), f2 = UUID.randomUUID(), c2 = UUID.randomUUID();
        ProfitabilityResponse a = AgProfitabilityAggregator.build(f1, List.of(new UnitInput("CROP_CYCLE", c1, "A", "HARVESTED", null, bd("100"))),
                List.of(new LedgerRow("CROP_CYCLE", c1, "LABOUR", bd("10")), new LedgerRow("CROP_CYCLE", c1, "FUEL", bd("20"))), List.of(new RevenueRow("CROP_CYCLE", c1, bd("500"))), 0, 0, List.of());
        ProfitabilityResponse b = AgProfitabilityAggregator.build(f2, List.of(new UnitInput("CROP_CYCLE", c2, "B", "HARVESTED", null, bd("200"))),
                List.of(new LedgerRow("CROP_CYCLE", c2, "EQUIPMENT", bd("30")), new LedgerRow("CROP_CYCLE", c2, "OTHER_DIRECT", bd("40"))), List.of(new RevenueRow("CROP_CYCLE", c2, bd("700"))), 0, 0, List.of());

        ProfitabilityOverviewResponse o = overview(new FarmReport(f1, "Alpha", a), new FarmReport(f2, "Beta", b));

        num("1200.00", o.totals().revenue()); num("300.00", o.totals().recordedCost()); num("10.00", o.totals().labour()); num("30.00", o.totals().equipment());
        num("20.00", o.totals().fuel()); num("40.00", o.totals().otherDirect()); num("400.00", o.totals().directCost()); num("800.00", o.totals().grossMargin());
    }

    @Test
    @DisplayName("the percentage is recomputed from the sums, never averaged: a small farm cannot drag it")
    void percentIsNotAnAverage() {
        // Farm A: revenue 100, margin 50 (50%). Farm B: revenue 900, margin 90 (10%). Average of percentages would be 30; the truth is 140 / 1000 = 14.
        ProfitabilityOverviewResponse o = overview(farm("A", "50", "100"), farm("B", "810", "900"));

        num("14.0", o.totals().marginPercent());
    }

    @Test
    @DisplayName("each farm row carries that farm's own figures, unchanged")
    void farmRowsAreTheFarmsOwnFigures() {
        FarmReport a = farm("Alpha", "100", "500");

        FarmMargin row = overview(a).farms().get(0);

        assertEquals(a.farmId(), row.farmId()); assertEquals("Alpha", row.farmName());
        num("500.00", row.revenue()); num("100.00", row.directCost()); num("400.00", row.grossMargin()); num("80.0", row.marginPercent());
        assertEquals(0, a.report().totals().grossMargin().compareTo(row.grossMargin()));
        assertEquals(1, row.finishedUnits()); assertEquals(0, row.runningUnits()); assertEquals(0, row.breedingStockUnits());
    }

    @Test
    @DisplayName("the finished, running and breeding-stock subtotals are summed, and add up to the total")
    void subtotalsAreSummed() {
        UUID f = UUID.randomUUID(), c1 = UUID.randomUUID(), c2 = UUID.randomUUID(), bull = UUID.randomUUID();
        ProfitabilityResponse a = AgProfitabilityAggregator.build(f, List.of(new UnitInput("CROP_CYCLE", c1, "Done", "HARVESTED", null, bd("100")), new UnitInput("CROP_CYCLE", c2, "Growing", "GROWING", null, bd("50")),
                new UnitInput("ANIMAL", bull, "Bull", "ACTIVE", "PURCHASED", bd("30"), true)), List.of(), List.of(new RevenueRow("CROP_CYCLE", c1, bd("400")), new RevenueRow("ANIMAL", bull, bd("20"))), 0, 0, List.of());

        ProfitabilityOverviewResponse o = overview(new FarmReport(f, "Alpha", a), farm("Beta", "10", "60"));

        assertEquals(2, o.complete().units()); assertEquals(1, o.inProgress().units()); assertEquals(1, o.breedingStock().units());
        num(o.complete().directCost().add(o.inProgress().directCost()).add(o.breedingStock().directCost()).toPlainString(), o.totals().directCost());
        num(o.complete().revenue().add(o.inProgress().revenue()).add(o.breedingStock().revenue()).toPlainString(), o.totals().revenue());
    }

    @Test
    @DisplayName("farms are listed by name, ignoring case, whatever order they arrive in")
    void sortedByName() {
        // chosen so a case-sensitive sort (capitals first: Bravo, Delta, alpha, charlie) would differ from the right order
        List<String> names = overview(farm("charlie", "1", "2"), farm("Delta", "1", "2"), farm("alpha", "1", "2"), farm("Bravo", "1", "2")).farms().stream().map(FarmMargin::farmName).toList();
        assertEquals(List.of("alpha", "Bravo", "charlie", "Delta"), names);
    }

    @Test
    @DisplayName("a farm with nothing recorded is still listed, with zeroes")
    void emptyFarmIsListed() {
        UUID f = UUID.randomUUID();
        FarmReport empty = new FarmReport(f, "Idle", AgProfitabilityAggregator.build(f, List.of(), List.of(), List.of(), 0, 0, List.of()));

        List<FarmMargin> rows = overview(empty, farm("Busy", "10", "50")).farms();      // listed by name: Busy, then Idle

        assertEquals("Busy", rows.get(0).farmName());
        assertEquals("Idle", rows.get(1).farmName()); num("0.00", rows.get(1).revenue()); num("0.00", rows.get(1).grossMargin()); assertNull(rows.get(1).marginPercent());
    }

    @Test
    @DisplayName("the standard note comes first and once; each farm's own warnings are prefixed with the farm's name")
    void notes() {
        ProfitabilityOverviewResponse o = overview(farm("Alpha", "10", "50", 2, List.of()), farm("Beta", "10", "50"));

        assertEquals(AgProfitabilityAggregator.STANDARD_NOTE, o.notes().get(0));
        assertEquals(1, o.notes().stream().filter(n -> n.startsWith("Gross margin only")).count(), "the standard note must not be repeated per farm");
        assertTrue(o.notes().stream().anyMatch(n -> n.startsWith("Alpha: ") && n.contains("2 recorded labour entries aren't costed yet")), o.notes().toString());
        assertTrue(o.notes().stream().noneMatch(n -> n.startsWith("Beta: ")), "a farm with no warnings adds none");
    }

    @Test
    @DisplayName("cautions count a farm's caveated units plus its own warnings")
    void cautions() {
        UUID f = UUID.randomUUID(), c = UUID.randomUUID(), g = UUID.randomUUID();
        ProfitabilityResponse r = AgProfitabilityAggregator.build(f, List.of(new UnitInput("CROP_CYCLE", c, "Sold nothing", "HARVESTED", null, bd("10")), new UnitInput("GROUP", g, "Batch", "ACTIVE", "PURCHASED", bd("5"))),
                List.of(), List.of(), 3, 0, List.of());

        FarmMargin row = overview(new FarmReport(f, "Alpha", r)).farms().get(0);

        assertEquals(3, row.cautions());                 // the harvested cycle with no sales, the purchased batch, and the uncosted-labour note
        assertEquals(0, overview(farm("Clean", "10", "50")).farms().get(0).cautions());
    }

    @Test
    @DisplayName("farm warnings are capped, with a summary of how many more there are")
    void notesAreCapped() {
        List<FarmReport> farms = new ArrayList<>();
        for (int i = 0; i < 40; i++) farms.add(farm(String.format("Farm %02d", i), "10", "50", 1, List.of()));

        ProfitabilityOverviewResponse o = AgProfitabilityOverviewAggregator.build(farms, List.of());

        assertEquals(1 + 30 + 1, o.notes().size());
        assertTrue(o.notes().get(o.notes().size() - 1).startsWith("10 more warning(s)"), o.notes().get(o.notes().size() - 1));
        assertEquals(40, o.farms().size());
    }

    @Test
    @DisplayName("extra notes from the service (for example, more farms than the cap) come last")
    void extraNotes() {
        ProfitabilityOverviewResponse o = AgProfitabilityOverviewAggregator.build(List.of(farm("Alpha", "10", "50", 1, List.of())), List.of("Only the first 50 farms are included."));
        assertEquals("Only the first 50 farms are included.", o.notes().get(o.notes().size() - 1));
    }

    @Test
    @DisplayName("no farms gives zero totals, no rows, and just the standard note")
    void noFarms() {
        ProfitabilityOverviewResponse o = AgProfitabilityOverviewAggregator.build(List.of(), List.of());

        assertTrue(o.farms().isEmpty()); num("0.00", o.totals().revenue()); num("0.00", o.totals().grossMargin()); assertNull(o.totals().marginPercent());
        assertEquals(List.of(AgProfitabilityAggregator.STANDARD_NOTE), o.notes());
        assertEquals(0, o.complete().units());
    }

    @Test
    @DisplayName("a loss on one farm is not hidden by a gain on another: the total nets them honestly")
    void lossesNet() {
        ProfitabilityOverviewResponse o = overview(farm("Gain", "100", "1000"), farm("Loss", "800", "200"));

        num("-600.00", o.farms().get(1).grossMargin());
        num("300.00", o.totals().grossMargin());
        num("25.0", o.totals().marginPercent());
    }
}
