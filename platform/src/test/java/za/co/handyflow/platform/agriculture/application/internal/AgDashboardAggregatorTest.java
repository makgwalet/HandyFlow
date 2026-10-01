package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.agriculture.application.internal.AgDashboardAggregator.*;
import za.co.handyflow.platform.agriculture.dto.AgDashboardResponse;
import za.co.handyflow.platform.agriculture.dto.AgDashboardResponse.FarmSummary;
import za.co.handyflow.platform.agriculture.dto.AttentionItemResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgDashboardAggregatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private static final UUID GREEN = UUID.randomUUID();      // 400 ha mixed farm
    private static final UUID RIVER = UUID.randomUUID();      // 100 ha crop farm
    private static final UUID NOSIZE = UUID.randomUUID();     // no size, no GPS
    private static final UUID INACTIVE = UUID.randomUUID();   // not in the farm list, so it must never count
    private static final UUID MAIZE = UUID.randomUUID(), SOY = UUID.randomUUID();
    private static final UUID CATTLE = UUID.randomUUID(), SHEEP = UUID.randomUUID(), UNKNOWN = UUID.randomUUID();

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static void assertNumber(String expected, BigDecimal actual) { assertEquals(0, bd(expected).compareTo(actual), "expected " + expected + " but was " + actual); }

    private static List<FarmRow> farms() {
        return List.of(
                new FarmRow(RIVER, "riverside", "CROP", "Free State", "Bethlehem", -28.2, 28.3, bd("100")),
                new FarmRow(GREEN, "Green Valley", "MIXED", "Gauteng", null, -26.1, 28.0, bd("400")),
                new FarmRow(NOSIZE, "New Farm", "CROP", null, null, null, null, null));
    }

    private static AgDashboardResponse build(List<AttentionItemResponse> attention, int limit) {
        return AgDashboardAggregator.build(TODAY, farms(),
                List.of(new CycleStatusRow(GREEN, "GROWING", 2, bd("126.5")), new CycleStatusRow(GREEN, "PLANTED", 1, bd("80")),
                        new CycleStatusRow(RIVER, "HARVESTING", 1, bd("22")), new CycleStatusRow(RIVER, "PLANNED", 3, bd("64")),
                        new CycleStatusRow(GREEN, "HARVESTED", 5, bd("300")), new CycleStatusRow(RIVER, "FAILED", 1, bd("40")),
                        new CycleStatusRow(INACTIVE, "GROWING", 9, bd("999"))),
                List.of(new CropAreaRow(GREEN, MAIZE, 2, bd("126.5")), new CropAreaRow(GREEN, SOY, 1, bd("80")),
                        new CropAreaRow(RIVER, MAIZE, 1, bd("22")), new CropAreaRow(INACTIVE, SOY, 9, bd("999"))),
                Map.of(MAIZE, "Maize", SOY, "Soybeans"),
                List.of(new AnimalRow(GREEN, CATTLE, 40), new AnimalRow(GREEN, SHEEP, 10), new AnimalRow(INACTIVE, CATTLE, 500)),
                List.of(new GroupRow(GREEN, SHEEP, 2, 150), new GroupRow(RIVER, CATTLE, 1, 30), new GroupRow(RIVER, UNKNOWN, 1, 5), new GroupRow(INACTIVE, SHEEP, 1, 700)),
                Map.of(CATTLE, new SpeciesInfo("Cattle", "LIVESTOCK"), SHEEP, new SpeciesInfo("Sheep", "LIVESTOCK")),
                attention, limit);
    }

    private static AttentionItemResponse item(UUID farm, String severity, LocalDate due, String title) {
        return new AttentionItemResponse("HEALTH_EVENT_DUE", severity, title, null, due, UUID.randomUUID(), farm, "x");
    }

    @Test
    @DisplayName("totals cover active farms only: rows for an inactive farm never leak in")
    void totals() {
        AgDashboardResponse.Totals t = build(List.of(), 20).totals();
        assertEquals(3, t.farmCount());
        assertNumber("500", t.totalHectares());                 // 100 + 400; the farm with no size adds nothing
        assertEquals(1, t.farmsWithoutHectares());
        assertEquals(4, t.cropCyclesInProduction());            // GROWING 2 + PLANTED 1 + HARVESTING 1; HARVESTED, FAILED, PLANNED excluded
        assertNumber("228.5", t.hectaresInProduction());        // 126.5 + 80 + 22
        assertEquals(3, t.plannedCropCycles());
        assertEquals(50, t.animalCount());
        assertEquals(4, t.groupCount());
        assertEquals(185, t.groupHead());
        assertEquals(235, t.totalHead());
    }

    @Test
    @DisplayName("farms are listed A to Z ignoring case, each with its own figures")
    void farmSummaries() {
        List<FarmSummary> list = build(List.of(), 20).farms();
        assertEquals(List.of("Green Valley", "New Farm", "riverside"), list.stream().map(FarmSummary::name).toList());
        FarmSummary green = list.get(0);
        assertNumber("206.5", green.hectaresInProduction());
        assertEquals(3, green.cropCyclesInProduction());
        assertEquals(50, green.animalCount());
        assertEquals(150, green.groupHead());
        FarmSummary river = list.get(2);
        assertEquals(35, river.groupHead());
        assertNumber("22", river.hectaresInProduction());
        FarmSummary none = list.get(1);
        assertNull(none.latitude());
        assertNull(none.totalHectares());
        assertNumber("0", none.hectaresInProduction());
        assertEquals(0, none.animalCount());
    }

    @Test
    @DisplayName("farm types are counted with their land, largest group first")
    void farmTypes() {
        var types = build(List.of(), 20).farmTypes();
        assertEquals("CROP", types.get(0).farmType());
        assertEquals(2, types.get(0).farmCount());
        assertNumber("100", types.get(0).hectares());          // the unsized farm contributes zero, not null
        assertEquals("MIXED", types.get(1).farmType());
    }

    @Test
    @DisplayName("a farm with no type is reported as UNSPECIFIED rather than dropped")
    void unspecifiedFarmType() {
        var r = AgDashboardAggregator.build(TODAY, List.of(new FarmRow(GREEN, "A", null, null, null, null, null, bd("10"))),
                List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of(), List.of(), 5);
        assertEquals("UNSPECIFIED", r.farmTypes().get(0).farmType());
    }

    @Test
    @DisplayName("crops: status breakdown in lifecycle order, and in-production hectares by crop, largest first")
    void crops() {
        var crops = build(List.of(), 20).crops();
        assertEquals(List.of("PLANNED", "PLANTED", "GROWING", "HARVESTING", "HARVESTED", "FAILED"), crops.byStatus().stream().map(s -> s.status()).toList());
        assertEquals(3, crops.byStatus().get(0).cycles());
        assertEquals(List.of("Maize", "Soybeans"), crops.inProduction().stream().map(c -> c.cropName()).toList());
        assertNumber("148.5", crops.inProduction().get(0).hectares());   // 126.5 + 22 across two farms
        assertEquals(3, crops.inProduction().get(0).cycles());
        assertNumber("80", crops.inProduction().get(1).hectares());      // the inactive farm's 999 ha is excluded
    }

    @Test
    @DisplayName("livestock: animals and group head merge per species; unknown species stay visible")
    void livestock() {
        var live = build(List.of(), 20).livestock();
        assertEquals(List.of("Sheep", "Cattle", "Unknown species"), live.stream().map(s -> s.name()).toList());
        assertEquals(10, live.get(0).animals());
        assertEquals(150, live.get(0).groupHead());
        assertEquals(160, live.get(0).totalHead());
        assertEquals(70, live.get(1).totalHead());                       // 40 animals + 30 in a group; the inactive farm's 500 is excluded
        assertNull(live.get(2).category());
        assertEquals(5, live.get(2).totalHead());
    }

    @Test
    @DisplayName("attention is ranked, counted by severity and attributed per farm")
    void attention() {
        var list = List.of(
                item(GREEN, "MEDIUM", null, "Lick is low"), item(GREEN, "OVERDUE", TODAY.minusDays(3), "Dosing"),
                item(RIVER, "CRITICAL", null, "Seed is out"), item(RIVER, "UPCOMING", TODAY.plusDays(2), "Harvest"),
                item(GREEN, "DUE_TODAY", TODAY, "Scouting"));
        var r = build(list, 20);
        assertEquals(5, r.attention().total());
        assertEquals("Seed is out", r.attention().items().get(0).title());
        assertEquals(List.of("CRITICAL", "OVERDUE", "DUE_TODAY", "UPCOMING", "MEDIUM"), r.attention().bySeverity().stream().map(s -> s.severity()).toList());
        FarmSummary green = r.farms().get(0);
        assertEquals(3, green.attentionCount());
        assertEquals(2, green.urgentCount());                            // overdue + due today; the medium one is not urgent
        assertEquals(2, r.farms().get(2).attentionCount());
        assertEquals(1, r.farms().get(2).urgentCount());                 // critical yes, upcoming no
    }

    @Test
    @DisplayName("the attention list is capped, but the totals and counts still reflect everything")
    void attentionLimit() {
        var many = new java.util.ArrayList<AttentionItemResponse>();
        for (int i = 0; i < 12; i++) many.add(item(GREEN, "OVERDUE", TODAY.minusDays(i + 1), "item " + i));
        var r = build(many, 5);
        assertEquals(5, r.attention().items().size());
        assertEquals(12, r.attention().total());
        assertEquals(12, r.attention().bySeverity().get(0).count());
        assertEquals(12, r.farms().get(0).attentionCount());
        assertEquals("item 11", r.attention().items().get(0).title());   // the most overdue first
    }

    @Test
    @DisplayName("an empty tenant produces an empty, well-formed dashboard")
    void empty() {
        var r = AgDashboardAggregator.build(TODAY, List.of(), List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of(), List.of(), 20);
        assertEquals(0, r.totals().farmCount());
        assertNumber("0", r.totals().totalHectares());
        assertNumber("0", r.totals().hectaresInProduction());
        assertEquals(0, r.totals().totalHead());
        assertTrue(r.farms().isEmpty());
        assertTrue(r.livestock().isEmpty());
        assertTrue(r.crops().byStatus().isEmpty());
        assertEquals(0, r.attention().total());
        assertEquals(TODAY, r.asOf());
    }

    @Test
    @DisplayName("a farm with a zero size counts as having no size")
    void zeroHectares() {
        var r = AgDashboardAggregator.build(TODAY, List.of(new FarmRow(GREEN, "A", "CROP", null, null, null, null, BigDecimal.ZERO)),
                List.of(), List.of(), Map.of(), List.of(), List.of(), Map.of(), List.of(), 5);
        assertEquals(1, r.totals().farmsWithoutHectares());
    }
}
