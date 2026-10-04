package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgProfitabilityRulesTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test
    @DisplayName("a crop cycle is complete once it is harvested, failed or abandoned, and not before")
    void cycleState() {
        for (String s : List.of("HARVESTED", "FAILED", "ABANDONED")) assertEquals("COMPLETE", AgProfitabilityRules.stateOf("CROP_CYCLE", s), s);
        for (String s : List.of("PLANNED", "PLANTED", "GROWING", "HARVESTING")) assertEquals("IN_PROGRESS", AgProfitabilityRules.stateOf("CROP_CYCLE", s), s);
    }

    @Test
    @DisplayName("a group is complete when closed; an animal when sold, dead, culled or transferred out")
    void groupAndAnimalState() {
        assertEquals("COMPLETE", AgProfitabilityRules.stateOf("GROUP", "CLOSED"));
        assertEquals("IN_PROGRESS", AgProfitabilityRules.stateOf("GROUP", "ACTIVE"));
        for (String s : List.of("SOLD", "DECEASED", "CULLED", "TRANSFERRED_OUT")) assertEquals("COMPLETE", AgProfitabilityRules.stateOf("ANIMAL", s), s);
        assertEquals("IN_PROGRESS", AgProfitabilityRules.stateOf("ANIMAL", "ACTIVE"));
    }

    @Test
    @DisplayName("an enterprise is ongoing, and anything unknown is treated as still running")
    void otherState() {
        assertEquals("IN_PROGRESS", AgProfitabilityRules.stateOf("ENTERPRISE", "ACTIVE"));
        assertEquals("IN_PROGRESS", AgProfitabilityRules.stateOf("ENTERPRISE", "CLOSED"));
        assertEquals("IN_PROGRESS", AgProfitabilityRules.stateOf(null, "SOLD"));
        assertEquals("IN_PROGRESS", AgProfitabilityRules.stateOf("ANIMAL", null));
        assertEquals("IN_PROGRESS", AgProfitabilityRules.stateOf("SOMETHING_NEW", "CLOSED"));
    }

    @Test
    @DisplayName("margin is revenue minus cost to the cent, and can be a loss")
    void margin() {
        assertEquals(0, bd("700.00").compareTo(AgProfitabilityRules.margin(bd("1000"), bd("300"))));
        assertEquals(0, bd("-250.50").compareTo(AgProfitabilityRules.margin(bd("100"), bd("350.50"))));
        assertEquals(0, bd("0.00").compareTo(AgProfitabilityRules.margin(bd("100"), bd("100"))));
        assertEquals(2, AgProfitabilityRules.margin(bd("1000"), bd("300")).scale());
    }

    @Test
    @DisplayName("margin percent is of revenue to one decimal, half up, and null without revenue")
    void marginPercent() {
        assertEquals(0, bd("70.0").compareTo(AgProfitabilityRules.marginPercent(bd("1000"), bd("700"))));
        assertEquals(0, bd("33.3").compareTo(AgProfitabilityRules.marginPercent(bd("300"), bd("100"))));
        assertEquals(0, bd("66.7").compareTo(AgProfitabilityRules.marginPercent(bd("300"), bd("200"))));
        assertEquals(0, bd("-250.5").compareTo(AgProfitabilityRules.marginPercent(bd("100"), bd("-250.5"))));
        assertEquals(0, bd("12.5").compareTo(AgProfitabilityRules.marginPercent(bd("8"), bd("1"))));
        assertNull(AgProfitabilityRules.marginPercent(BigDecimal.ZERO, bd("-100")));
        assertNull(AgProfitabilityRules.marginPercent(null, bd("-100")));
    }

    @Test
    @DisplayName("a finished unit that would normally have been sold, with no sales attributed, says so")
    void noSalesCaveat() {
        for (String[] u : new String[][] {{"CROP_CYCLE", "HARVESTED"}, {"GROUP", "CLOSED"}, {"ANIMAL", "SOLD"}}) {
            List<String> c = AgProfitabilityRules.caveats(u[0], u[1], null, BigDecimal.ZERO);
            assertEquals(1, c.size(), u[0]);
            assertTrue(c.get(0).contains("No sales are attributed"), c.get(0));
        }
        assertEquals(1, AgProfitabilityRules.caveats("ANIMAL", "SOLD", null, null).size());
    }

    @Test
    @DisplayName("no such caveat when there are sales, when the unit is still running, or when not selling was the outcome")
    void noCaveatWhenFine() {
        assertTrue(AgProfitabilityRules.caveats("CROP_CYCLE", "HARVESTED", null, bd("100")).isEmpty());
        assertTrue(AgProfitabilityRules.caveats("CROP_CYCLE", "GROWING", null, BigDecimal.ZERO).isEmpty());
        assertTrue(AgProfitabilityRules.caveats("CROP_CYCLE", "FAILED", null, BigDecimal.ZERO).isEmpty());
        assertTrue(AgProfitabilityRules.caveats("ANIMAL", "DECEASED", null, BigDecimal.ZERO).isEmpty());
        assertTrue(AgProfitabilityRules.caveats("ANIMAL", "TRANSFERRED_OUT", null, BigDecimal.ZERO).isEmpty());
        assertTrue(AgProfitabilityRules.caveats("GROUP", "ACTIVE", null, BigDecimal.ZERO).isEmpty());
    }

    @Test
    @DisplayName("breeding stock explains what its row includes, and gets no 'no sales' warning because it is not expected to be sold")
    void breedingStockCaveat() {
        List<String> c = AgProfitabilityRules.caveats("ANIMAL", "SOLD", "PURCHASED", BigDecimal.ZERO, true);
        assertEquals(1, c.size());
        assertTrue(c.get(0).contains("purchase price is capital") && c.get(0).contains("isn't a production margin"), c.get(0));
        assertTrue(c.stream().noneMatch(x -> x.contains("No sales")));
        assertEquals(1, AgProfitabilityRules.caveats("ANIMAL", "ACTIVE", null, bd("500"), true).size());
    }

    @Test
    @DisplayName("not breeding stock behaves exactly as before, whichever overload is used")
    void notBreedingStockIsUnchanged() {
        assertEquals(AgProfitabilityRules.caveats("ANIMAL", "SOLD", null, BigDecimal.ZERO), AgProfitabilityRules.caveats("ANIMAL", "SOLD", null, BigDecimal.ZERO, false));
        assertEquals("BREEDING_STOCK", AgProfitabilityRules.BREEDING_STOCK);
    }

    @Test
    @DisplayName("a purchased batch warns that its purchase price is not recorded; one born on the farm does not")
    void purchasedGroup() {
        List<String> c = AgProfitabilityRules.caveats("GROUP", "ACTIVE", "PURCHASED", bd("100"));
        assertEquals(1, c.size());
        assertTrue(c.get(0).contains("purchase price") && c.get(0).contains("overstated"), c.get(0));
        assertTrue(AgProfitabilityRules.caveats("GROUP", "ACTIVE", "BORN_ON_FARM", bd("100")).isEmpty());
        assertTrue(AgProfitabilityRules.caveats("ANIMAL", "ACTIVE", "PURCHASED", bd("100")).isEmpty());          // an animal's purchase price IS recorded
        assertEquals(2, AgProfitabilityRules.caveats("GROUP", "CLOSED", "PURCHASED", BigDecimal.ZERO).size());
    }
}
