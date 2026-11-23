package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AgPackRulesTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test void onlyBagsBalesAndSacksAreCountUnits() {
        for (String u : new String[]{"bag", "Bags", " bale ", "BALES", "sack", "sacks"}) assertTrue(AgPackRules.isCountUnit(u), u);
        for (String u : new String[]{"kg", "t", "litre", "crate", "", null}) assertFalse(AgPackRules.isCountUnit(u), String.valueOf(u));
        assertEquals("bag", AgPackRules.singular("Bags"));
        assertEquals("sack", AgPackRules.singular("sack"));
        assertEquals("kg", AgPackRules.singular("kg"));
    }

    @Test void aPackSizeNeedsACountUnitAndAbelievableValue() {
        assertNull(AgPackRules.requirePackSize("bag", null));
        assertNull(AgPackRules.requirePackSize("kg", null));
        assertEquals("50.000", AgPackRules.requirePackSize("bag", bd("50")).toPlainString());
        assertEquals("10000.000", AgPackRules.requirePackSize("sack", bd("10000")).toPlainString());
        assertThrows(IllegalArgumentException.class, () -> AgPackRules.requirePackSize("kg", bd("50")));
        assertThrows(IllegalArgumentException.class, () -> AgPackRules.requirePackSize("t", bd("50")));
        assertThrows(IllegalArgumentException.class, () -> AgPackRules.requirePackSize("bag", bd("0")));
        assertThrows(IllegalArgumentException.class, () -> AgPackRules.requirePackSize("bag", bd("-5")));
        assertThrows(IllegalArgumentException.class, () -> AgPackRules.requirePackSize("bag", bd("10000.001")));
    }

    @Test void kilogramsConvertToBagsAndBack() {
        assertEquals("2.400", AgPackRules.kgToUnits(bd("120"), bd("50")).toPlainString());
        assertEquals("0.500", AgPackRules.kgToUnits(bd("25"), bd("50")).toPlainString());
        assertEquals("0.333", AgPackRules.kgToUnits(bd("10"), bd("30")).toPlainString());
        assertEquals("120.000", AgPackRules.unitsToKg(bd("2.4"), bd("50")).toPlainString());
        assertEquals("37.500", AgPackRules.unitsToKg(bd("1.5"), bd("25")).toPlainString());
    }

    @Test void costPerKgIsPricePerBagOverKgPerBag() {
        assertEquals("9.0000", AgPackRules.costPerKg(bd("450"), bd("50")).toPlainString());
        assertEquals("8.6667", AgPackRules.costPerKg(bd("260"), bd("30")).toPlainString());
        assertNull(AgPackRules.costPerKg(null, bd("50")));
        assertNull(AgPackRules.costPerKg(bd("450"), null));
        assertNull(AgPackRules.costPerKg(bd("450"), bd("0")));
    }

    // ---- feed from stock, through AgFeedRules ----

    @Test void feedInKgIsTakenOffABagItemThroughItsPackSize() {
        assertEquals("2.400", AgFeedRules.quantityInItemUnit(bd("120"), "bags", "Starter", bd("50")).toPlainString());
    }

    @Test void aBagItemWithoutAPackSizeBlocksFeedAndSaysWhatToDo() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AgFeedRules.quantityInItemUnit(bd("120"), "bag", "Starter", null));
        assertTrue(e.getMessage().contains("'Starter' is counted in 'bag'"), e.getMessage());
        assertTrue(e.getMessage().contains("Set kg per bag"), e.getMessage());
    }

    @Test void massItemsConvertAsBeforeEvenWithAPackSizeArgument() {
        assertEquals(0, AgFeedRules.quantityInItemUnit(bd("500"), "t", "Maize", null).compareTo(bd("0.5")));
        assertEquals(0, AgFeedRules.quantityInItemUnit(bd("500"), "kg", "Maize", bd("50")).compareTo(bd("500")));
    }

    @Test void costPerKgFromABagItemDividesThePrice() {
        assertEquals("9.0000", AgFeedRules.costPerKg(null, bd("450"), "bag", bd("50")).toPlainString());
        assertNull(AgFeedRules.costPerKg(null, bd("450"), "bag", null));
        assertEquals("7.5", AgFeedRules.costPerKg(bd("7.5"), bd("450"), "bag", bd("50")).toPlainString());
        assertEquals("0.0100", AgFeedRules.costPerKg(null, bd("10"), "t", null).setScale(4, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    @Test void feedIsEnteredInKgOrInBagsNotBothNorNeither() {
        assertEquals("120", AgFeedRules.resolveQuantityKg(bd("120"), null, true, "Starter", "bag", bd("50")).toPlainString());
        assertEquals("120", AgFeedRules.resolveQuantityKg(bd("120"), null, false, null, null, null).toPlainString());
        assertEquals("120.000", AgFeedRules.resolveQuantityKg(null, bd("2.4"), true, "Starter", "bag", bd("50")).toPlainString());
        assertThrows(IllegalArgumentException.class, () -> AgFeedRules.resolveQuantityKg(bd("1"), bd("1"), true, "S", "bag", bd("50")));
        assertThrows(IllegalArgumentException.class, () -> AgFeedRules.resolveQuantityKg(null, null, true, "S", "bag", bd("50")));
    }

    @Test void bagsNeedAStockItemCountedInBagsWithAPackSize() {
        IllegalArgumentException noItem = assertThrows(IllegalArgumentException.class, () -> AgFeedRules.resolveQuantityKg(null, bd("2"), false, null, null, null));
        assertTrue(noItem.getMessage().contains("only when it comes from a stock item"), noItem.getMessage());
        IllegalArgumentException notBags = assertThrows(IllegalArgumentException.class, () -> AgFeedRules.resolveQuantityKg(null, bd("2"), true, "Maize", "kg", bd("50")));
        assertTrue(notBags.getMessage().contains("is not counted in bags"), notBags.getMessage());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgFeedRules.resolveQuantityKg(null, bd("2"), true, "Starter", "bag", null));
        assertTrue(e.getMessage().contains("Set it on the item first"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> AgFeedRules.resolveQuantityKg(null, bd("0"), true, "Starter", "bag", bd("50")));
    }
}
