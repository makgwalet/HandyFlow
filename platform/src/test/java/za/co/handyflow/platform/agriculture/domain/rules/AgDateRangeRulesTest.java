package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class AgDateRangeRulesTest {

    private static final LocalDate JUL = LocalDate.of(2026, 7, 1);
    private static final LocalDate SEP = LocalDate.of(2026, 9, 30);

    @Test void aMissingEndIsReplacedByAnEndThatExcludesNothing() {
        assertEquals(AgDateRangeRules.EARLIEST, AgDateRangeRules.from(null));
        assertEquals(AgDateRangeRules.LATEST, AgDateRangeRules.to(null));
        assertTrue(AgDateRangeRules.EARLIEST.isBefore(LocalDate.of(1990, 1, 1)));
        assertTrue(AgDateRangeRules.LATEST.isAfter(LocalDate.of(2100, 1, 1)));
    }

    @Test void aGivenEndIsKept() {
        assertEquals(JUL, AgDateRangeRules.from(JUL));
        assertEquals(SEP, AgDateRangeRules.to(SEP));
    }

    @Test void onlyNoEndsAtAllIsUnbounded() {
        assertTrue(AgDateRangeRules.isUnbounded(null, null));
        assertFalse(AgDateRangeRules.isUnbounded(JUL, null), "a from alone is a filter");
        assertFalse(AgDateRangeRules.isUnbounded(null, SEP), "a to alone is a filter");
        assertFalse(AgDateRangeRules.isUnbounded(JUL, SEP));
    }

    @Test void theEndsMustBeInOrderButTheSameDayIsFine() {
        AgDateRangeRules.require(JUL, SEP);
        AgDateRangeRules.require(JUL, JUL);
        AgDateRangeRules.require(null, SEP);
        AgDateRangeRules.require(JUL, null);
        AgDateRangeRules.require(null, null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgDateRangeRules.require(SEP, JUL));
        assertTrue(e.getMessage().contains("'from'"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> AgDateRangeRules.require(JUL.plusDays(1), JUL));
    }
}
