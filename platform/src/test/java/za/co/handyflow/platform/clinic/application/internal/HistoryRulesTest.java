package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HistoryRulesTest {

    @Test
    void oneOfAcceptsAnyCaseAndSpacesAndUsesTheDefaultForBlank() {
        assertEquals("AUNT_UNCLE", HistoryRules.oneOf(" aunt uncle ", HistoryRules.RELATIVES, "Relative", null));
        assertEquals("UNKNOWN", HistoryRules.oneOf("  ", HistoryRules.SMOKING, "Smoking", "UNKNOWN"));
        assertNull(HistoryRules.oneOf(null, HistoryRules.RELATIVES, "Relative", null));
    }

    @Test
    void oneOfNamesTheFieldAndTheAllowedValues() {
        var e = assertThrows(IllegalArgumentException.class, () -> HistoryRules.oneOf("lots", HistoryRules.ALCOHOL, "Alcohol", "UNKNOWN"));
        assertTrue(e.getMessage().startsWith("Alcohol is not one of"));
        assertTrue(e.getMessage().contains("HEAVY"));
    }

    @Test
    void textTrimsBlankBecomesNullAndTooLongIsRefused() {
        assertEquals("Teacher", HistoryRules.text("  Teacher ", 150, "Occupation"));
        assertNull(HistoryRules.text("   ", 150, "Occupation"));
        assertThrows(IllegalArgumentException.class, () -> HistoryRules.text("x".repeat(151), 150, "Occupation"));
        assertEquals("Condition is required.", assertThrows(IllegalArgumentException.class, () -> HistoryRules.required(" ", 200, "Condition")).getMessage());
    }

    @Test
    void ageAtOnsetMustBeAPlausibleAge() {
        assertNull(HistoryRules.ageAtOnset(null));
        assertEquals((short) 0, HistoryRules.ageAtOnset(0));
        assertEquals((short) 120, HistoryRules.ageAtOnset(120));
        assertThrows(IllegalArgumentException.class, () -> HistoryRules.ageAtOnset(-1));
        assertThrows(IllegalArgumentException.class, () -> HistoryRules.ageAtOnset(121));
    }

    @Test
    void medicalAidNeedsSchemeAndMemberNumberAndTrimsTheRest() {
        var a = HistoryRules.aid(" Discovery ", " Classic Saver ", " 12345678 ", "01", "", " 0211234567 ");
        assertEquals("Discovery", a.schemeName());
        assertEquals("12345678", a.memberNumber());
        assertEquals("01", a.dependentCode());
        assertNull(a.principalMember());
        assertEquals("0211234567", a.phone());
        assertEquals("Scheme name is required.", assertThrows(IllegalArgumentException.class, () -> HistoryRules.aid("", null, "1", null, null, null)).getMessage());
        assertEquals("Member number is required.", assertThrows(IllegalArgumentException.class, () -> HistoryRules.aid("Bonitas", null, " ", null, null, null)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> HistoryRules.aid("Bonitas", null, "1", "12345678901", null, null));
    }
}
