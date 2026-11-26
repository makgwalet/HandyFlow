package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PriceRulesTest {

    private static final LocalDate D = LocalDate.of(2027, 3, 10);
    private static final String NOTE = "Price list of 2027 as approved";

    @Test
    void creationChecks() {
        assertNull(PriceRules.problem("TUITION", null, new BigDecimal("1500.50"), true, D, null, NOTE));
        assertEquals("Choose the kind of fee", PriceRules.problem("BOGUS", null, BigDecimal.TEN, true, D, null, NOTE));
        assertEquals("Say what the fee is for", PriceRules.problem("OTHER", " ", BigDecimal.TEN, true, D, null, NOTE));
        assertEquals("The amount must be more than 0", PriceRules.problem("TUITION", null, BigDecimal.ZERO, true, D, null, NOTE));
        assertEquals("The amount can have at most two decimal places", PriceRules.problem("TUITION", null, new BigDecimal("10.005"), true, D, null, NOTE));
        assertEquals("Say whether the amount includes VAT", PriceRules.problem("TUITION", null, BigDecimal.TEN, null, D, null, NOTE));
        assertEquals("Give the day the fee starts", PriceRules.problem("TUITION", null, BigDecimal.TEN, true, null, null, NOTE));
        assertEquals("The end date cannot be before the start date", PriceRules.problem("TUITION", null, BigDecimal.TEN, true, D, D.minusDays(1), NOTE));
        assertTrue(PriceRules.problem("TUITION", null, BigDecimal.TEN, true, D, null, "short").contains("at least 10"));
    }

    @Test
    void periodsOverlapOnSharedDaysAndOpenEndsRunForever() {
        assertTrue(PriceRules.overlaps(D, null, D.plusDays(100), null));
        assertTrue(PriceRules.overlaps(D, D.plusDays(10), D.plusDays(10), null));
        assertFalse(PriceRules.overlaps(D, D.plusDays(10), D.plusDays(11), null));
        assertFalse(PriceRules.overlaps(D.plusDays(11), null, D, D.plusDays(10)));
        assertTrue(PriceRules.overlaps(D, D.plusDays(30), D.plusDays(5), D.plusDays(6)));
    }

    @Test
    void inForceIncludesBothEndDays() {
        assertTrue(PriceRules.inForce(D, D.plusDays(5), D));
        assertTrue(PriceRules.inForce(D, D.plusDays(5), D.plusDays(5)));
        assertFalse(PriceRules.inForce(D, D.plusDays(5), D.plusDays(6)));
        assertFalse(PriceRules.inForce(D, null, D.minusDays(1)));
        assertTrue(PriceRules.inForce(D, null, D.plusDays(900)));
    }

    @Test
    void sameFeeLooksAtDescriptionOnlyForOther() {
        assertTrue(PriceRules.sameFee("TUITION", null, "TUITION", "ignored"));
        assertFalse(PriceRules.sameFee("TUITION", null, "MATERIALS", null));
        assertTrue(PriceRules.sameFee("OTHER", "Exam resit", "OTHER", " exam RESIT "));
        assertFalse(PriceRules.sameFee("OTHER", "Exam resit", "OTHER", "Late fee"));
    }

    @Test
    void withdrawNeedsAReason() {
        assertNotNull(PriceRules.withdrawProblem("short"));
        assertNull(PriceRules.withdrawProblem("Replaced by the new price list"));
    }
}
