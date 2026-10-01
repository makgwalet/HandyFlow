package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgUnitsTest {

    private static void assertNumber(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    @DisplayName("aliases and case fold to one canonical unit")
    void canonicalFoldsAliases() {
        assertEquals("t", AgUnits.canonical("Tonnes"));
        assertEquals("t", AgUnits.canonical(" T "));
        assertEquals("t", AgUnits.canonical("tons"));
        assertEquals("kg", AgUnits.canonical("KG"));
        assertEquals("kg", AgUnits.canonical("Kilograms"));
        assertEquals("lb", AgUnits.canonical("lbs"));
        assertEquals("bags", AgUnits.canonical(" Bags "), "unknown units are only normalised for case and spacing");
        assertEquals("", AgUnits.canonical(null));
    }

    @Test
    @DisplayName("mass units convert in both directions")
    void convertsMass() {
        assertNumber("0.8", AgUnits.convert(new BigDecimal("800"), "kg", "t").orElseThrow());
        assertNumber("5000", AgUnits.convert(new BigDecimal("5"), "t", "kg").orElseThrow());
        assertNumber("1.5", AgUnits.convert(new BigDecimal("1500"), "g", "kg").orElseThrow());
        assertNumber("0.453592", AgUnits.convert(BigDecimal.ONE, "lb", "kg").orElseThrow());   // results are rounded to six decimals
        assertNumber("5", AgUnits.convert(new BigDecimal("5000"), "Kilograms", "Tonnes").orElseThrow());
    }

    @Test
    @DisplayName("the same unit passes through untouched, even for non-mass units")
    void sameUnitPassesThrough() {
        assertNumber("12.5", AgUnits.convert(new BigDecimal("12.5"), "bags", "Bags").orElseThrow());
        assertTrue(AgUnits.sameUnit("bales", " BALES "));
        assertTrue(AgUnits.canConvert("bags", "bags"));
    }

    @Test
    @DisplayName("incompatible, mixed or missing units do not convert")
    void incompatibleUnitsDoNotConvert() {
        assertTrue(AgUnits.convert(BigDecimal.TEN, "bags", "t").isEmpty());
        assertTrue(AgUnits.convert(BigDecimal.TEN, "t", "bales").isEmpty());
        assertTrue(AgUnits.convert(BigDecimal.TEN, "", "t").isEmpty());
        assertTrue(AgUnits.convert(BigDecimal.TEN, "kg", null).isEmpty());
        assertTrue(AgUnits.convert(null, "kg", "t").isEmpty());
        assertFalse(AgUnits.canConvert("bags", "t"));
        assertFalse(AgUnits.sameUnit("", ""), "two blank units are not 'the same unit'");
    }

    @Test
    @DisplayName("isMass recognises mass units and rejects counts")
    void isMass() {
        assertTrue(AgUnits.isMass("tonnes"));
        assertTrue(AgUnits.isMass("g"));
        assertFalse(AgUnits.isMass("bags"));
        assertFalse(AgUnits.isMass(null));
    }

    @Test
    @DisplayName("conversion keeps six decimal places and rounds half up")
    void conversionPrecision() {
        // 1 kg in tonnes is 0.001; 1 g in tonnes is 0.000001; half a gram rounds to 0.000001 (half up), not 0
        assertNumber("0.001", AgUnits.convert(BigDecimal.ONE, "kg", "t").orElseThrow());
        assertNumber("0.000001", AgUnits.convert(BigDecimal.ONE, "g", "t").orElseThrow());
        assertNumber("0.000001", AgUnits.convert(new BigDecimal("0.5"), "g", "t").orElseThrow());
    }

    @Test
    @DisplayName("requireConvertible accepts mass units for a mass crop, and the exact unit otherwise")
    void requireConvertible() {
        assertDoesNotThrow(() -> AgUnits.requireConvertible("kg", "t"));
        assertDoesNotThrow(() -> AgUnits.requireConvertible("Tonnes", "kg"));
        assertDoesNotThrow(() -> AgUnits.requireConvertible("bags", "Bags"));
        assertDoesNotThrow(() -> AgUnits.requireConvertible("anything", null));
        assertDoesNotThrow(() -> AgUnits.requireConvertible("anything", " "));
    }

    @Test
    @DisplayName("requireConvertible refuses incompatible units with advice on what to use")
    void requireConvertibleRefuses() {
        IllegalArgumentException massCrop = assertThrows(IllegalArgumentException.class, () -> AgUnits.requireConvertible("bags", "t"));
        assertTrue(massCrop.getMessage().contains("'bags'"));
        assertTrue(massCrop.getMessage().contains("'t'"));
        assertTrue(massCrop.getMessage().contains("mass unit"));
        IllegalArgumentException countCrop = assertThrows(IllegalArgumentException.class, () -> AgUnits.requireConvertible("kg", "bales"));
        assertTrue(countCrop.getMessage().contains("use 'bales'"));
        assertThrows(IllegalArgumentException.class, () -> AgUnits.requireConvertible(null, "kg"));
    }

    @Test
    @DisplayName("yield sums mixed kg and tonnes in the crop's unit")
    void sumsMixedMassUnits() {
        AgUnits.YieldTotal inTonnes = AgUnits.sumInto("t", List.of(
                new AgUnits.UnitQuantity("t", new BigDecimal("5")), new AgUnits.UnitQuantity("kg", new BigDecimal("800"))));
        assertNumber("5.8", inTonnes.total());
        assertEquals(0, inTonnes.unconvertedUnits());
        AgUnits.YieldTotal inKg = AgUnits.sumInto("kg", List.of(
                new AgUnits.UnitQuantity("Tonnes", new BigDecimal("5")), new AgUnits.UnitQuantity("kg", new BigDecimal("800"))));
        assertNumber("5800", inKg.total());
    }

    @Test
    @DisplayName("units that cannot convert are left out and flagged, not added in")
    void unconvertibleUnitsAreFlagged() {
        AgUnits.YieldTotal r = AgUnits.sumInto("t", List.of(
                new AgUnits.UnitQuantity("t", new BigDecimal("5")), new AgUnits.UnitQuantity("bags", new BigDecimal("40")),
                new AgUnits.UnitQuantity("Bags", new BigDecimal("10"))));
        assertNumber("5", r.total());
        assertEquals(1, r.unconvertedUnits(), "bags and Bags are one unit");
    }

    @Test
    @DisplayName("with no crop unit, quantities are summed as recorded and a mix is still flagged")
    void noTargetUnit() {
        AgUnits.YieldTotal same = AgUnits.sumInto(null, List.of(new AgUnits.UnitQuantity("kg", new BigDecimal("3")), new AgUnits.UnitQuantity("KG", new BigDecimal("4"))));
        assertNumber("7", same.total());
        assertEquals(0, same.unconvertedUnits());
        AgUnits.YieldTotal mixed = AgUnits.sumInto(null, List.of(new AgUnits.UnitQuantity("kg", new BigDecimal("3")), new AgUnits.UnitQuantity("t", new BigDecimal("4"))));
        assertEquals(1, mixed.unconvertedUnits());
        assertNumber("0", AgUnits.sumInto("t", List.of()).total());
    }
}
