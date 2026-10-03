package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AgEquipmentFuelRulesTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static void assertNumber(String expected, BigDecimal actual) { assertEquals(0, bd(expected).compareTo(actual), "expected " + expected + " but was " + actual); }

    @Test
    @DisplayName("the source types are the ones the ledger and the unique index use")
    void sourceTypes() {
        assertEquals("FLEET_USAGE", AgEquipmentFuelRules.EQUIPMENT_SOURCE);
        assertEquals("FUEL_DISPATCH", AgEquipmentFuelRules.FUEL_SOURCE);
    }

    @Test
    @DisplayName("a use is above zero hours and at most a day")
    void hoursOfUse() {
        assertDoesNotThrow(() -> AgEquipmentFuelRules.requireHoursOfUse(bd("0.5")));
        assertDoesNotThrow(() -> AgEquipmentFuelRules.requireHoursOfUse(bd("24")));
        IllegalArgumentException tooLong = assertThrows(IllegalArgumentException.class, () -> AgEquipmentFuelRules.requireHoursOfUse(bd("24.1")));
        assertTrue(tooLong.getMessage().contains("24"), tooLong.getMessage());
        assertThrows(IllegalArgumentException.class, () -> AgEquipmentFuelRules.requireHoursOfUse(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> AgEquipmentFuelRules.requireHoursOfUse(bd("-1")));
        assertThrows(IllegalArgumentException.class, () -> AgEquipmentFuelRules.requireHoursOfUse(null));
    }

    @Test
    @DisplayName("the rate is stored to four places, rounding half up")
    void snapshotRate() {
        assertNumber("85.5000", AgEquipmentFuelRules.snapshotRate(bd("85.5")));
        assertNumber("12.3456", AgEquipmentFuelRules.snapshotRate(bd("12.345649")));       // fifth decimal is 4: rounds down
        assertNumber("12.3457", AgEquipmentFuelRules.snapshotRate(bd("12.34565")));
        assertEquals(4, AgEquipmentFuelRules.snapshotRate(bd("85.5")).scale());
    }

    @Test
    @DisplayName("equipment cost is hours x the snapshotted rate, to the cent")
    void equipmentAmount() {
        assertNumber("513.00", AgEquipmentFuelRules.equipmentAmount(bd("6"), bd("85.5000")));
        assertNumber("42.75", AgEquipmentFuelRules.equipmentAmount(bd("0.5"), bd("85.5000")));
        assertNumber("25.65", AgEquipmentFuelRules.equipmentAmount(bd("1"), bd("25.6460")));                 // 25.646 rounds UP to the cent
        assertNumber("100.00", AgEquipmentFuelRules.equipmentAmount(bd("3"), bd("33.3333")));                // 99.9999 is R100.00
        assertEquals(2, AgEquipmentFuelRules.equipmentAmount(bd("6"), bd("85.5")).scale());
    }

    @Test
    @DisplayName("fuel cost is litres x the tank's snapshotted cost per litre, to the cent")
    void fuelAmount() {
        assertNumber("11200.00", AgEquipmentFuelRules.fuelAmount(bd("500"), bd("22.4000")));
        assertNumber("1120.15", AgEquipmentFuelRules.fuelAmount(bd("50.5"), bd("22.1812")));              // 1120.1506
        assertNumber("11.20", AgEquipmentFuelRules.fuelAmount(bd("0.5"), bd("22.4000")));
        assertNumber("22.45", AgEquipmentFuelRules.fuelAmount(bd("1"), bd("22.4460")));                    // rounds UP, not down
        assertEquals(2, AgEquipmentFuelRules.fuelAmount(bd("500"), bd("22.4")).scale());
    }
}
