package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LearnerRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    /** Builds a 13-digit number with a valid Luhn check digit for the given first twelve digits. */
    private static String withCheck(String first12) {
        for (int d = 0; d <= 9; d++) {
            String n = first12 + d;
            if (LearnerRules.luhnValid(n)) {
                return n;
            }
        }
        throw new IllegalStateException();
    }

    @Test
    void luhnAcceptsAValidNumberAndRejectsAChangedDigit() {
        String ok = withCheck("900101500008");
        assertTrue(LearnerRules.luhnValid(ok));
        char last = ok.charAt(12);
        String bad = ok.substring(0, 12) + (last == '9' ? '0' : (char) (last + 1));
        assertFalse(LearnerRules.luhnValid(bad));
        assertFalse(LearnerRules.luhnValid("12a4"));
        assertFalse(LearnerRules.luhnValid(""));
    }

    @Test
    void saIdNeedsThirteenDigitsAndACheckDigit() {
        assertEquals("A South African ID number has 13 digits", LearnerRules.idProblem("SA_ID", "12345", null, TODAY));
        String ok = withCheck("900101500008");
        assertNull(LearnerRules.idProblem("SA_ID", ok, null, TODAY));
        String bad = ok.substring(0, 12) + (ok.charAt(12) == '9' ? '0' : (char) (ok.charAt(12) + 1));
        assertTrue(LearnerRules.idProblem("SA_ID", bad, null, TODAY).contains("check digit"));
    }

    @Test
    void dateOfBirthMustAgreeWithTheIdNumber() {
        String ok = withCheck("900101500008");
        assertNull(LearnerRules.idProblem("SA_ID", ok, LocalDate.of(1990, 1, 1), TODAY));
        assertTrue(LearnerRules.idProblem("SA_ID", ok, LocalDate.of(1990, 2, 1), TODAY).contains("does not match"));
    }

    @Test
    void anImpossibleDateInsideTheNumberIsRefused() {
        String n = withCheck("901301500008");
        assertTrue(LearnerRules.idProblem("SA_ID", n, null, TODAY).contains("not a real date"));
    }

    @Test
    void otherDocumentTypesNeedANumberAndNoneNeedsNoNumber() {
        assertEquals("Enter the identity number", LearnerRules.idProblem("PASSPORT", "  ", null, TODAY));
        assertNull(LearnerRules.idProblem("PASSPORT", "A1234567", null, TODAY));
        assertNull(LearnerRules.idProblem("NONE", null, null, TODAY));
        assertTrue(LearnerRules.idProblem("NONE", "123", null, TODAY).contains("blank"));
        assertEquals("Choose how the learner is identified", LearnerRules.idProblem("DRIVING", "1", null, TODAY));
    }

    @Test
    void aBirthDateInTheFutureIsRefused() {
        assertTrue(LearnerRules.idProblem("PASSPORT", "A1234567", TODAY.plusDays(1), TODAY).contains("future"));
    }

    @Test
    void consentNeedsItsDate() {
        assertNull(LearnerRules.consentProblem(false, null, TODAY));
        assertNull(LearnerRules.consentProblem(true, TODAY, TODAY));
        assertTrue(LearnerRules.consentProblem(true, null, TODAY).contains("date"));
        assertTrue(LearnerRules.consentProblem(false, TODAY, TODAY).contains("without consent"));
        assertTrue(LearnerRules.consentProblem(true, TODAY.plusDays(1), TODAY).contains("future"));
    }

    @Test
    void emailAndNamesAreChecked() {
        assertNull(LearnerRules.contactProblem(null));
        assertNull(LearnerRules.contactProblem("a@b.co"));
        assertNotNull(LearnerRules.contactProblem("not-an-email"));
        assertNotNull(LearnerRules.namesProblem("A", "Smith"));
        assertNull(LearnerRules.namesProblem("Test", "Learner"));
    }

    @Test
    void maskShowsOnlyTheLastFour() {
        assertEquals("*********0081", LearnerRules.mask("9001015000081"));
        assertEquals("****", LearnerRules.mask("123"));
        assertNull(LearnerRules.mask(" "));
        assertNull(LearnerRules.mask(null));
    }
}
