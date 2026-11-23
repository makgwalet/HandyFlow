package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgFeedRulesTest {

    private static final UUID FARM = UUID.randomUUID();

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test void itemMustBeOnTheSameFarmAndActive() {
        AgFeedRules.requireStockItem(FARM, "ACTIVE", FARM, "Starter");
        assertThrows(IllegalArgumentException.class, () -> AgFeedRules.requireStockItem(UUID.randomUUID(), "ACTIVE", FARM, "Starter"));
        assertThrows(IllegalArgumentException.class, () -> AgFeedRules.requireStockItem(null, "ACTIVE", FARM, "Starter"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgFeedRules.requireStockItem(FARM, "INACTIVE", FARM, "Starter"));
        assertTrue(e.getMessage().contains("inactive"));
    }

    @Test void quantityMustBePositive() {
        AgFeedRules.requirePositive(bd("0.1"));
        assertThrows(IllegalArgumentException.class, () -> AgFeedRules.requirePositive(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> AgFeedRules.requirePositive(bd("-1")));
        assertThrows(IllegalArgumentException.class, () -> AgFeedRules.requirePositive(null));
    }

    @Test void kilogramsAreConvertedIntoTheItemsUnit() {
        assertEquals(0, bd("50").compareTo(AgFeedRules.quantityInItemUnit(bd("50"), "kg", "Starter")));
        assertEquals(0, bd("0.05").compareTo(AgFeedRules.quantityInItemUnit(bd("50"), "t", "Starter")));
        assertEquals(0, bd("0.05").compareTo(AgFeedRules.quantityInItemUnit(bd("50"), "Tonnes", "Starter")));
        assertEquals(0, bd("50000").compareTo(AgFeedRules.quantityInItemUnit(bd("50"), "g", "Starter")));
    }

    @Test void bagsCannotBeConvertedAndSayWhy() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgFeedRules.quantityInItemUnit(bd("50"), "bags", "Starter"));
        assertTrue(e.getMessage().contains("'Starter' is counted in 'bags'"));
        assertThrows(IllegalArgumentException.class, () -> AgFeedRules.quantityInItemUnit(bd("50"), null, "Starter"));
    }

    @Test void typedCostWins() {
        assertEquals(0, bd("9").compareTo(AgFeedRules.costPerKg(bd("9"), bd("5"), "kg")));
    }

    @Test void itemCostIsUsedWhenNothingTyped() {
        assertEquals(0, bd("5").compareTo(AgFeedRules.costPerKg(null, bd("5"), "kg")));
    }

    @Test void itemCostIsConvertedToPerKg() {
        assertEquals(0, bd("8.5").compareTo(AgFeedRules.costPerKg(null, bd("8500"), "t")));
        assertEquals(0, bd("2000").compareTo(AgFeedRules.costPerKg(null, bd("2"), "g")));
    }

    @Test void noCostWhenNeitherKnownOrUnitIsNotMass() {
        assertNull(AgFeedRules.costPerKg(null, null, "kg"));
        assertNull(AgFeedRules.costPerKg(null, bd("120"), "bags"));
    }
}
