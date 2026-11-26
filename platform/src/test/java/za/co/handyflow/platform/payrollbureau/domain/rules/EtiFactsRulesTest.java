package za.co.handyflow.platform.payrollbureau.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class EtiFactsRulesTest {

    private static final LocalDate REF = LocalDate.of(2026, 11, 26);

    @Test
    void acceptsAValidIdNumber() {
        assertTrue(EtiFactsRules.isValidSaId("8001015009087"));
        assertTrue(EtiFactsRules.isValidSaId(" 8001015009087 "));
    }

    @Test
    void rejectsBadChecksumLengthAndDate() {
        assertFalse(EtiFactsRules.isValidSaId("8001015009088"));
        assertFalse(EtiFactsRules.isValidSaId("800101500908"));
        assertFalse(EtiFactsRules.isValidSaId("8013015009087"));
        assertFalse(EtiFactsRules.isValidSaId(null));
        assertFalse(EtiFactsRules.isValidSaId("abcdefghijklm"));
    }

    @Test
    void readsTheDateOfBirthAndPicksTheCentury() {
        assertEquals(LocalDate.of(1980, 1, 1), EtiFactsRules.dateOfBirth("8001015009087", REF));
        assertEquals(LocalDate.of(2005, 3, 9), EtiFactsRules.dateOfBirth("0503095009087", REF));
        assertEquals(LocalDate.of(1927, 3, 9), EtiFactsRules.dateOfBirth("2703095009087", REF));
        assertTrue(EtiFactsRules.dateOfBirth("9902305009087", REF) == null);
    }

    @Test
    void aManualChoiceWins() {
        assertEquals("SA_ID", EtiFactsRules.idKind("8001015009087", null));
        assertEquals("OTHER", EtiFactsRules.idKind("A1234567", null));
        assertEquals("ASYLUM", EtiFactsRules.idKind("A1234567", "ASYLUM"));
        assertEquals("REFUGEE", EtiFactsRules.idKind("8001015009087", "REFUGEE"));
    }
}
