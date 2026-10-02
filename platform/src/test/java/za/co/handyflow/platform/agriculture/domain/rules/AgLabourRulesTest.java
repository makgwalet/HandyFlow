package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AgLabourRulesTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private static void assertNumber(String expected, BigDecimal actual) { assertNotNull(actual, "expected " + expected + " but was null"); assertEquals(0, bd(expected).compareTo(actual), "expected " + expected + " but was " + actual); }
    private static final BigDecimal H45 = bd("45");

    @Test
    @DisplayName("ordinary hours in a pay period: a week, two weeks, or an average month of 52/12 weeks")
    void hoursPerPeriod() {
        assertNumber("45", AgLabourRules.hoursPerPayPeriod("WEEKLY", H45));
        assertNumber("90", AgLabourRules.hoursPerPayPeriod("FORTNIGHTLY", H45));
        assertNumber("195", AgLabourRules.hoursPerPayPeriod("MONTHLY", H45));                       // 45 x 52 / 12
        assertNumber("173.333333", AgLabourRules.hoursPerPayPeriod("MONTHLY", bd("40")));           // 40 x 52 / 12
    }

    @Test
    @DisplayName("the pay frequency is read case-insensitively; one this does not know gives nothing, never a guess")
    void frequencyHandling() {
        assertNumber("45", AgLabourRules.hoursPerPayPeriod(" weekly ", H45));
        assertNull(AgLabourRules.hoursPerPayPeriod("DAILY", H45));
        assertNull(AgLabourRules.hoursPerPayPeriod("", H45));
        assertNull(AgLabourRules.hoursPerPayPeriod(null, H45));
        assertNull(AgLabourRules.hoursPerPayPeriod("MONTHLY", null));
        assertNull(AgLabourRules.hoursPerPayPeriod("MONTHLY", BigDecimal.ZERO));
    }

    @Test
    @DisplayName("hourly rate is the period's salary over the period's ordinary hours, to four places")
    void hourlyRate() {
        assertNumber("100.0000", AgLabourRules.hourlyRate(bd("19500"), "MONTHLY", H45));            // 19 500 / 195
        assertNumber("100.0000", AgLabourRules.hourlyRate(bd("4500"), "WEEKLY", H45));
        assertNumber("100.0000", AgLabourRules.hourlyRate(bd("9000"), "FORTNIGHTLY", H45));
        assertNumber("25.6410", AgLabourRules.hourlyRate(bd("5000"), "MONTHLY", H45));              // 5 000 / 195 = 25.641025...
        assertEquals(4, AgLabourRules.hourlyRate(bd("19500"), "MONTHLY", H45).scale());
    }

    @Test
    @DisplayName("fewer ordinary hours a week means a higher hourly rate for the same salary")
    void hoursSettingMatters() {
        assertTrue(AgLabourRules.hourlyRate(bd("19500"), "MONTHLY", bd("40")).compareTo(AgLabourRules.hourlyRate(bd("19500"), "MONTHLY", H45)) > 0);
    }

    @Test
    @DisplayName("no salary, a zero salary, or an unknown frequency gives no rate")
    void noRate() {
        assertNull(AgLabourRules.hourlyRate(null, "MONTHLY", H45));
        assertNull(AgLabourRules.hourlyRate(BigDecimal.ZERO, "MONTHLY", H45));
        assertNull(AgLabourRules.hourlyRate(bd("-5"), "MONTHLY", H45));
        assertNull(AgLabourRules.hourlyRate(bd("19500"), "ANNUALLY", H45));
    }

    @Test
    @DisplayName("on-costs load the rate by a percentage of it, to four places")
    void loadedRate() {
        assertNumber("102.0000", AgLabourRules.loadedRate(bd("100"), bd("2")));
        assertNumber("100.0000", AgLabourRules.loadedRate(bd("100"), BigDecimal.ZERO));
        assertNumber("100.0000", AgLabourRules.loadedRate(bd("100"), null));
        assertNumber("125.0000", AgLabourRules.loadedRate(bd("100"), bd("25")));
        assertNumber("30.8077", AgLabourRules.loadedRate(bd("25.6410"), bd("20.15")));
        assertEquals(4, AgLabourRules.loadedRate(bd("100"), bd("2")).scale());
    }

    @Test
    @DisplayName("the amount is hours x the SNAPSHOTTED rate, to the cent, so it always reproduces from the two stored numbers")
    void amount() {
        assertNumber("612.00", AgLabourRules.amount(bd("6"), bd("102.0000")));
        assertNumber("25.64", AgLabourRules.amount(bd("1"), bd("25.6410")));
        assertNumber("12.82", AgLabourRules.amount(bd("0.5"), bd("25.6410")));                      // 12.8205
        assertNumber("25.65", AgLabourRules.amount(bd("1"), bd("25.6460")));                       // 25.646 rounds UP to the cent, not down
        assertNumber("100.00", AgLabourRules.amount(bd("3"), bd("33.3333")));                      // 99.9999 is R100.00
        assertNumber("0.00", AgLabourRules.amount(bd("0.001"), bd("1.0000")));                      // rounds to nothing: the service refuses this
        assertEquals(2, AgLabourRules.amount(bd("6"), bd("102")).scale());
    }

    @Test
    @DisplayName("settings: weekly hours above 0 and at most 84, on-cost between 0 and 100")
    void settings() {
        assertDoesNotThrow(() -> AgLabourRules.requireValidSettings(bd("45"), bd("0")));
        assertDoesNotThrow(() -> AgLabourRules.requireValidSettings(bd("84"), bd("100")));
        assertDoesNotThrow(() -> AgLabourRules.requireValidSettings(bd("0.5"), bd("2.35")));
        for (String h : new String[] {"0", "-1", "84.01", "200"}) assertThrows(IllegalArgumentException.class, () -> AgLabourRules.requireValidSettings(bd(h), bd("2")), h);
        for (String p : new String[] {"-0.01", "100.01", "500"}) assertThrows(IllegalArgumentException.class, () -> AgLabourRules.requireValidSettings(bd("45"), bd(p)), p);
        assertThrows(IllegalArgumentException.class, () -> AgLabourRules.requireValidSettings(null, bd("2")));
        assertThrows(IllegalArgumentException.class, () -> AgLabourRules.requireValidSettings(bd("45"), null));
    }

    @Test
    @DisplayName("a worked example end to end: R19 500 a month, 2% on-costs, 6 hours")
    void workedExample() {
        BigDecimal base = AgLabourRules.hourlyRate(bd("19500"), "MONTHLY", H45);
        BigDecimal loaded = AgLabourRules.loadedRate(base, bd("2"));
        assertNumber("100.0000", base);
        assertNumber("102.0000", loaded);
        assertNumber("612.00", AgLabourRules.amount(bd("6"), loaded));
    }
}
