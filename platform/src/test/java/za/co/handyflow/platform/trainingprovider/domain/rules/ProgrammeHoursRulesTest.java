package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ProgrammeHoursRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    private static ProgrammeHoursRules.Entry ok() {
        return new ProgrammeHoursRules.Entry(new BigDecimal("120"), "CURRICULUM", "Curriculum document", LocalDate.of(2026, 1, 5), "Section 4", LocalDate.of(2026, 2, 1), "Sam Dube", LocalDate.of(2027, 3, 1));
    }

    private static ProgrammeHoursRules.Entry with(BigDecimal h, String type, String ref, LocalDate ver, String sec, LocalDate eff, String by, LocalDate on) {
        return new ProgrammeHoursRules.Entry(h, type, ref, ver, sec, eff, by, on);
    }

    @Test
    void completeEntryIsAccepted() {
        assertNull(ProgrammeHoursRules.problem(ok(), TODAY));
    }

    @Test
    void hoursMustBePositiveAndBounded() {
        assertNotNull(ProgrammeHoursRules.problem(with(BigDecimal.ZERO, "CURRICULUM", "Doc", null, "p1", TODAY, "Sam", TODAY), TODAY));
        assertNotNull(ProgrammeHoursRules.problem(with(new BigDecimal("20001"), "CURRICULUM", "Doc", null, "p1", TODAY, "Sam", TODAY), TODAY));
        assertNotNull(ProgrammeHoursRules.problem(with(new BigDecimal("1.234"), "CURRICULUM", "Doc", null, "p1", TODAY, "Sam", TODAY), TODAY));
        assertNull(ProgrammeHoursRules.problem(with(new BigDecimal("1.50"), "CURRICULUM", "Doc", null, "p1", TODAY, "Sam", TODAY), TODAY));
    }

    @Test
    void everySourceItemIsRequired() {
        assertEquals("Say what kind of source the hours come from", ProgrammeHoursRules.problem(with(BigDecimal.TEN, "GUESS", "Doc", null, "p1", TODAY, "Sam", TODAY), TODAY));
        assertEquals("Give the document or reference the hours come from", ProgrammeHoursRules.problem(with(BigDecimal.TEN, "OTHER", " a ", null, "p1", TODAY, "Sam", TODAY), TODAY));
        assertEquals("Give the section or page where the hours are stated", ProgrammeHoursRules.problem(with(BigDecimal.TEN, "OTHER", "Doc", null, " ", TODAY, "Sam", TODAY), TODAY));
        assertEquals("Give the date the source says the hours take effect", ProgrammeHoursRules.problem(with(BigDecimal.TEN, "OTHER", "Doc", null, "p1", null, "Sam", TODAY), TODAY));
        assertEquals("Say who checked the source", ProgrammeHoursRules.problem(with(BigDecimal.TEN, "OTHER", "Doc", null, "p1", TODAY, "", TODAY), TODAY));
        assertEquals("Give the day it was checked", ProgrammeHoursRules.problem(with(BigDecimal.TEN, "OTHER", "Doc", null, "p1", TODAY, "Sam", null), TODAY));
    }

    @Test
    void datesMustBeSensible() {
        assertEquals("The check cannot be dated in the future", ProgrammeHoursRules.problem(with(BigDecimal.TEN, "OTHER", "Doc", null, "p1", TODAY, "Sam", TODAY.plusDays(1)), TODAY));
        assertEquals("The source's own date cannot be after the day it was checked", ProgrammeHoursRules.problem(with(BigDecimal.TEN, "OTHER", "Doc", TODAY, "p1", TODAY, "Sam", TODAY.minusDays(1)), TODAY));
    }

    @Test
    void withdrawalNeedsAReason() {
        assertNotNull(ProgrammeHoursRules.withdrawProblem("ACTIVE", "short"));
        assertNotNull(ProgrammeHoursRules.withdrawProblem("WITHDRAWN", "A long enough reason"));
        assertNull(ProgrammeHoursRules.withdrawProblem("ACTIVE", "Entered against the wrong version"));
    }

    @Test
    void noteNeverSaysMetOrPassed() {
        String n = ProgrammeHoursRules.note(new BigDecimal("120.00"), new BigDecimal("40"), new BigDecimal("30"));
        assertTrue(n.contains("records 120 workplace hours"));
        assertTrue(n.contains("40 are logged, 30 of them confirmed"));
        assertFalse(n.toLowerCase().contains("met") || n.toLowerCase().contains("passed") || n.toLowerCase().contains("complete"));
    }
}
