package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.agriculture.application.internal.AgProfitabilityAggregator.LedgerRow;
import za.co.handyflow.platform.agriculture.application.internal.AgProfitabilityAggregator.RevenueRow;
import za.co.handyflow.platform.agriculture.application.internal.AgProfitabilityAggregator.UnitInput;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.ProfitabilityResponse;
import za.co.handyflow.platform.agriculture.dto.ProfitabilityDtos.UnitProfit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgProfitabilityAggregatorTest {

    static final UUID FARM = UUID.randomUUID();
    final UUID cycle = UUID.randomUUID(), cycle2 = UUID.randomUUID(), group = UUID.randomUUID(), animal = UUID.randomUUID(), ent = UUID.randomUUID();

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static void num(String expected, BigDecimal actual) { assertEquals(0, bd(expected).compareTo(actual), "expected " + expected + " but was " + actual); }
    private UnitInput harvested(UUID id, String recorded) { return new UnitInput("CROP_CYCLE", id, "Maize " + id.toString().substring(0, 4), "HARVESTED", null, bd(recorded)); }
    private static LedgerRow ledger(String type, UUID id, String category, String amount) { return new LedgerRow(type, id, category, bd(amount)); }
    private static ProfitabilityResponse build(List<UnitInput> u, List<LedgerRow> l, List<RevenueRow> r) { return AgProfitabilityAggregator.build(FARM, u, l, r, 0, 0, List.of()); }
    private static UnitProfit only(ProfitabilityResponse p) { assertEquals(1, p.units().size()); return p.units().get(0); }

    @Test
    @DisplayName("a unit's cost is its recorded cost plus each ledger category, and its margin is revenue minus that")
    void costsAndMargin() {
        ProfitabilityResponse p = build(List.of(harvested(cycle, "1000")),
                List.of(ledger("CROP_CYCLE", cycle, "LABOUR", "200"), ledger("CROP_CYCLE", cycle, "EQUIPMENT", "300"), ledger("CROP_CYCLE", cycle, "FUEL", "400"), ledger("CROP_CYCLE", cycle, "OTHER_DIRECT", "100")),
                List.of(new RevenueRow("CROP_CYCLE", cycle, bd("5000"))));

        UnitProfit u = only(p);
        num("5000.00", u.revenue()); num("1000.00", u.recordedCost()); num("200.00", u.labour()); num("300.00", u.equipment()); num("400.00", u.fuel()); num("100.00", u.otherDirect());
        num("2000.00", u.directCost()); num("3000.00", u.grossMargin()); num("60.0", u.marginPercent());
        assertEquals("COMPLETE", u.state());
        assertTrue(u.caveats().isEmpty());
    }

    @Test
    @DisplayName("costs and sales of one target never leak into another, even of the same type")
    void targetsAreSeparate() {
        ProfitabilityResponse p = build(List.of(harvested(cycle, "100"), harvested(cycle2, "50")),
                List.of(ledger("CROP_CYCLE", cycle, "LABOUR", "10"), ledger("CROP_CYCLE", cycle2, "LABOUR", "7"), ledger("GROUP", cycle, "LABOUR", "999")),
                List.of(new RevenueRow("CROP_CYCLE", cycle, bd("500")), new RevenueRow("CROP_CYCLE", cycle2, bd("20"))));

        UnitProfit a = p.units().stream().filter(x -> cycle.equals(x.targetId())).findFirst().orElseThrow();
        UnitProfit b = p.units().stream().filter(x -> cycle2.equals(x.targetId())).findFirst().orElseThrow();
        num("110.00", a.directCost()); num("500.00", a.revenue());
        num("57.00", b.directCost()); num("20.00", b.revenue());
        num("999.00", p.units().stream().filter(x -> "UNLISTED".equals(x.targetType())).findFirst().orElseThrow().labour());   // the GROUP row has no such group listed
    }

    @Test
    @DisplayName("several sales on the same target add up, they do not overwrite one another")
    void salesAdd() {
        ProfitabilityResponse p = build(List.of(harvested(cycle, "100")), List.of(),
                List.of(new RevenueRow("CROP_CYCLE", cycle, bd("300")), new RevenueRow("CROP_CYCLE", cycle, bd("450.50")), new RevenueRow("CROP_CYCLE", cycle, bd("-50"))));
        num("700.50", only(p).revenue());
        num("600.50", only(p).grossMargin());
    }

    @Test
    @DisplayName("a reversal nets a ledger cost to nothing")
    void reversalNets() {
        ProfitabilityResponse p = build(List.of(harvested(cycle, "0")), List.of(ledger("CROP_CYCLE", cycle, "LABOUR", "250.00"), ledger("CROP_CYCLE", cycle, "LABOUR", "-100.00")), List.of(new RevenueRow("CROP_CYCLE", cycle, bd("1000"))));
        num("150.00", only(p).labour());
        num("850.00", only(p).grossMargin());
    }

    @Test
    @DisplayName("a ledger category this code does not know goes to other direct costs instead of being dropped")
    void unknownCategory() {
        ProfitabilityResponse p = build(List.of(harvested(cycle, "0")), List.of(ledger("CROP_CYCLE", cycle, "TRANSPORT", "80"), ledger("CROP_CYCLE", cycle, null, "20")), List.of(new RevenueRow("CROP_CYCLE", cycle, bd("1000"))));
        num("100.00", only(p).otherDirect());
        num("100.00", only(p).directCost());
    }

    @Test
    @DisplayName("a loss is negative, with a negative percentage; no revenue gives no percentage")
    void lossAndNoRevenue() {
        ProfitabilityResponse loss = build(List.of(harvested(cycle, "300")), List.of(), List.of(new RevenueRow("CROP_CYCLE", cycle, bd("100"))));
        num("-200.00", only(loss).grossMargin()); num("-200.0", only(loss).marginPercent());
        ProfitabilityResponse none = build(List.of(harvested(cycle, "300")), List.of(), List.of());
        num("-300.00", only(none).grossMargin()); assertNull(only(none).marginPercent());
    }

    @Test
    @DisplayName("a unit with no money in or out is left out")
    void emptyOmitted() {
        ProfitabilityResponse p = build(List.of(harvested(cycle, "0"), harvested(cycle2, "5")), List.of(), List.of());
        assertEquals(1, p.units().size());
        assertEquals(cycle2, p.units().get(0).targetId());
    }

    @Test
    @DisplayName("cost or revenue on a target that is no longer listed goes in one catch-all row, so nothing is lost")
    void unlistedCatchAll() {
        UUID gone = UUID.randomUUID();
        ProfitabilityResponse p = build(List.of(harvested(cycle, "100")), List.of(ledger("ANIMAL", gone, "FUEL", "40")), List.of(new RevenueRow("ANIMAL", gone, bd("300")), new RevenueRow("ENTERPRISE", UUID.randomUUID(), bd("60"))));

        UnitProfit catchAll = p.units().stream().filter(x -> "UNLISTED".equals(x.targetType())).findFirst().orElseThrow();
        num("360.00", catchAll.revenue()); num("40.00", catchAll.fuel());
        assertEquals("IN_PROGRESS", catchAll.state());
        assertFalse(catchAll.caveats().isEmpty());
        num("360.00", p.totals().revenue());
    }

    @Test
    @DisplayName("the farm total is exactly the sum of the unit rows, and equals everything in the ledger, in recorded costs and in sales")
    void totalsAreTheSumOfEverything() {
        UUID gone = UUID.randomUUID();
        List<UnitInput> units = List.of(harvested(cycle, "1000.10"), new UnitInput("GROUP", group, "Batch 7", "ACTIVE", "BORN_ON_FARM", bd("250.25")),
                new UnitInput("ANIMAL", animal, "TAG 1", "SOLD", null, bd("3000")), new UnitInput("ENTERPRISE", ent, "Maize enterprise", "ACTIVE", null, BigDecimal.ZERO));
        List<LedgerRow> l = List.of(ledger("CROP_CYCLE", cycle, "LABOUR", "100.33"), ledger("GROUP", group, "FUEL", "50.50"), ledger("ENTERPRISE", ent, "OTHER_DIRECT", "75.75"), ledger("ANIMAL", gone, "EQUIPMENT", "10.10"));
        List<RevenueRow> r = List.of(new RevenueRow("CROP_CYCLE", cycle, bd("4000.40")), new RevenueRow("ANIMAL", animal, bd("5200.55")), new RevenueRow("ANIMAL", gone, bd("1.05")));

        ProfitabilityResponse p = build(units, l, r);

        BigDecimal sumRevenue = p.units().stream().map(UnitProfit::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumCost = p.units().stream().map(UnitProfit::directCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumMargin = p.units().stream().map(UnitProfit::grossMargin).reduce(BigDecimal.ZERO, BigDecimal::add);
        num(sumRevenue.toPlainString(), p.totals().revenue()); num(sumCost.toPlainString(), p.totals().directCost()); num(sumMargin.toPlainString(), p.totals().grossMargin());

        num("9202.00", p.totals().revenue());                                   // 4000.40 + 5200.55 + 1.05 in sales
        num("4487.03", p.totals().directCost());                                // 1000.10 + 250.25 + 3000 recorded, plus 100.33 + 50.50 + 75.75 + 10.10 ledger
        num("4714.97", p.totals().grossMargin());
        num("4250.35", p.totals().recordedCost()); num("100.33", p.totals().labour()); num("10.10", p.totals().equipment()); num("50.50", p.totals().fuel()); num("75.75", p.totals().otherDirect());
        num(p.complete().directCost().add(p.inProgress().directCost()).toPlainString(), p.totals().directCost());
        num(p.complete().revenue().add(p.inProgress().revenue()).toPlainString(), p.totals().revenue());
        assertEquals(p.units().size(), p.complete().units() + p.inProgress().units());
    }

    @Test
    @DisplayName("finished units have a final margin and running units are shown to date, separately")
    void completeVersusInProgress() {
        ProfitabilityResponse p = build(List.of(harvested(cycle, "100"), new UnitInput("CROP_CYCLE", cycle2, "Wheat", "GROWING", null, bd("400"))), List.of(), List.of(new RevenueRow("CROP_CYCLE", cycle, bd("600"))));

        assertEquals(1, p.complete().units()); assertEquals(1, p.inProgress().units());
        num("500.00", p.complete().grossMargin()); num("83.3", p.complete().marginPercent());
        num("-400.00", p.inProgress().grossMargin()); assertNull(p.inProgress().marginPercent());
        assertEquals("COMPLETE", p.units().get(0).state());                     // finished units come first
    }

    @Test
    @DisplayName("units are ordered finished first, then by type and label")
    void ordering() {
        UnitInput g = new UnitInput("GROUP", group, "b batch", "ACTIVE", null, bd("1"));
        UnitInput g2 = new UnitInput("GROUP", UUID.randomUUID(), "A batch", "ACTIVE", null, bd("1"));
        UnitInput c = new UnitInput("CROP_CYCLE", cycle, "Wheat", "GROWING", null, bd("1"));
        UnitInput done = new UnitInput("ANIMAL", animal, "TAG 9", "SOLD", null, bd("1"));

        List<String> labels = build(List.of(g, g2, c, done), List.of(), List.of()).units().stream().map(UnitProfit::label).toList();

        assertEquals(List.of("TAG 9", "Wheat", "A batch", "b batch"), labels);
    }

    @Test
    @DisplayName("caveats are attached to the unit they are about")
    void caveatsAttached() {
        ProfitabilityResponse p = build(List.of(new UnitInput("GROUP", group, "Batch 7", "CLOSED", "PURCHASED", bd("10"))), List.of(), List.of());
        assertEquals(2, only(p).caveats().size());
    }

    @Test
    @DisplayName("notes always say it is gross margin, and add the uncosted-labour and uncounted-sales warnings only when they apply")
    void notes() {
        List<String> plain = AgProfitabilityAggregator.build(FARM, List.of(), List.of(), List.of(), 0, 0, List.of()).notes();
        assertEquals(1, plain.size());
        assertTrue(plain.get(0).startsWith("Gross margin only"));

        List<String> one = AgProfitabilityAggregator.build(FARM, List.of(), List.of(), List.of(), 1, 1, List.of()).notes();
        assertEquals(3, one.size());
        assertTrue(one.get(1).contains("1 recorded labour entry isn't costed yet"), one.get(1));
        assertTrue(one.get(2).contains("1 sale allocation isn't counted"), one.get(2));

        List<String> many = AgProfitabilityAggregator.build(FARM, List.of(), List.of(), List.of(), 4, 2, List.of()).notes();
        assertTrue(many.get(1).contains("4 recorded labour entries aren't costed yet"), many.get(1));
        assertTrue(many.get(2).contains("2 sale allocations aren't counted"), many.get(2));
    }

    @Test
    @DisplayName("extra notes from the service (for example, a report that was cut off) come after the standard ones")
    void extraNotes() {
        List<String> notes = AgProfitabilityAggregator.build(FARM, List.of(), List.of(), List.of(), 1, 0, List.of("Only the first 1000 animals are included.")).notes();
        assertEquals(3, notes.size());
        assertEquals("Only the first 1000 animals are included.", notes.get(2));
    }

    private UnitInput bull(String recorded) { return new UnitInput("ANIMAL", animal, "TAG 1 (Samson)", "ACTIVE", "PURCHASED", bd(recorded), true); }

    @Test
    @DisplayName("breeding stock is its own row and state, kept out of the finished and running subtotals")
    void breedingStockIsSeparate() {
        ProfitabilityResponse p = build(List.of(bull("800"), harvested(cycle, "100")), List.of(ledger("ANIMAL", animal, "FUEL", "50")), List.of(new RevenueRow("CROP_CYCLE", cycle, bd("1000"))));

        UnitProfit b = p.units().stream().filter(u -> "ANIMAL".equals(u.targetType())).findFirst().orElseThrow();
        assertEquals("BREEDING_STOCK", b.state());
        num("850.00", b.directCost());
        assertEquals(1, p.breedingStock().units());
        num("850.00", p.breedingStock().directCost());
        assertEquals(1, p.complete().units());
        assertEquals(0, p.inProgress().units());
        num("100.00", p.complete().directCost());
    }

    @Test
    @DisplayName("breeding stock's running costs and sales are in the farm totals: the three subtotals add up to the total")
    void breedingStockIsInTheTotals() {
        UUID cow = UUID.randomUUID();
        List<UnitInput> units = List.of(harvested(cycle, "100"), new UnitInput("CROP_CYCLE", cycle2, "Wheat", "GROWING", null, bd("400")), bull("800"));
        List<LedgerRow> l = List.of(ledger("ANIMAL", animal, "FUEL", "50"), ledger("CROP_CYCLE", cycle, "LABOUR", "25"));
        List<RevenueRow> r = List.of(new RevenueRow("CROP_CYCLE", cycle, bd("1000")), new RevenueRow("ANIMAL", animal, bd("300")));

        ProfitabilityResponse p = build(units, l, r);

        num("1300.00", p.totals().revenue());
        num("1375.00", p.totals().directCost());
        num(p.complete().directCost().add(p.inProgress().directCost()).add(p.breedingStock().directCost()).toPlainString(), p.totals().directCost());
        num(p.complete().revenue().add(p.inProgress().revenue()).add(p.breedingStock().revenue()).toPlainString(), p.totals().revenue());
        assertEquals(p.units().size(), p.complete().units() + p.inProgress().units() + p.breedingStock().units());
    }

    @Test
    @DisplayName("breeding stock carries its explanation, and no 'no sales' warning even when its status is sold")
    void breedingStockCaveats() {
        UnitInput sold = new UnitInput("ANIMAL", animal, "TAG 9", "SOLD", "PURCHASED", bd("100"), true);

        List<String> caveats = only(build(List.of(sold), List.of(), List.of())).caveats();

        assertEquals(1, caveats.size());
        assertTrue(caveats.get(0).contains("purchase price is capital"), caveats.get(0));
    }

    @Test
    @DisplayName("breeding stock sorts after the finished and running units")
    void breedingStockSortsLast() {
        UnitInput running = new UnitInput("GROUP", group, "Batch", "ACTIVE", null, bd("1"));
        UnitInput done = new UnitInput("ANIMAL", UUID.randomUUID(), "TAG 5", "SOLD", null, bd("1"));

        List<String> labels = build(List.of(bull("5"), running, done), List.of(), List.of()).units().stream().map(UnitProfit::label).toList();

        assertEquals(List.of("TAG 5", "Batch", "TAG 1 (Samson)"), labels);
    }

    @Test
    @DisplayName("the breeding-stock note appears only when there is breeding stock")
    void breedingStockNote() {
        assertTrue(build(List.of(bull("5")), List.of(), List.of()).notes().stream().anyMatch(n -> n.contains("Breeding stock is shown apart")));
        assertTrue(build(List.of(harvested(cycle, "5")), List.of(), List.of()).notes().stream().noneMatch(n -> n.contains("Breeding stock")));
    }

    @Test
    @DisplayName("a unit that is not flagged is unchanged by the new field")
    void notFlaggedUnchanged() {
        UnitInput sold = new UnitInput("ANIMAL", animal, "TAG 1", "SOLD", null, bd("3000"));
        UnitProfit u = only(build(List.of(sold), List.of(), List.of(new RevenueRow("ANIMAL", animal, bd("5000")))));
        assertEquals("COMPLETE", u.state());
        num("2000.00", u.grossMargin());
        assertEquals(0, build(List.of(sold), List.of(), List.of()).breedingStock().units());
    }

    @Test
    @DisplayName("an empty farm reports zeroes and no units")
    void emptyFarm() {
        ProfitabilityResponse p = AgProfitabilityAggregator.build(FARM, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0, 0, List.of());
        assertTrue(p.units().isEmpty());
        num("0.00", p.totals().revenue()); num("0.00", p.totals().grossMargin()); assertNull(p.totals().marginPercent());
        assertEquals(0, p.complete().units());
    }
}
