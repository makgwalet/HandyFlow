package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class Emp201RulesTest {

    private static final LocalDate END = LocalDate.of(2026, 10, 31);
    private static final BigDecimal DUE = new BigDecimal("12345.67");

    @Test
    void dueOnThe7thOfTheNextMonthRolledBackOverWeekends() {
        assertEquals(LocalDate.of(2026, 11, 6), Emp201Rules.dueDate(END));                        // 7 Nov 2026 is a Saturday
        assertEquals(LocalDate.of(2026, 12, 7), Emp201Rules.dueDate(LocalDate.of(2026, 11, 30))); // Monday
        assertEquals(LocalDate.of(2027, 2, 5), Emp201Rules.dueDate(LocalDate.of(2027, 1, 31)));   // 7 Feb 2027 is a Sunday
        assertEquals(LocalDate.of(2027, 1, 7), Emp201Rules.dueDate(LocalDate.of(2026, 12, 15)));  // mid-month period end
    }

    @Test
    void aReferenceIsNeededToFileAndTheRunMustBeIssued() {
        assertNull(Emp201Rules.submitProblem("DRAFT", true, "7 1234 5678 9012"));
        assertNotNull(Emp201Rules.submitProblem("DRAFT", false, "712345678901"));
        assertNotNull(Emp201Rules.submitProblem("DRAFT", true, " "));
        assertNotNull(Emp201Rules.submitProblem("DRAFT", true, null));
        assertNotNull(Emp201Rules.submitProblem("DRAFT", true, "12-34"));
        assertNotNull(Emp201Rules.submitProblem("SUBMITTED", true, "712345678901"));
        assertNotNull(Emp201Rules.submitProblem("PAID", true, "712345678901"));
        assertEquals("712345678901", Emp201Rules.cleanReference(" 7123 4567 8901 "));
    }

    @Test
    void payingNeedsAFiledDeclarationAndTheFullAmount() {
        LocalDate today = LocalDate.of(2026, 11, 5);
        assertNull(Emp201Rules.payProblem("SUBMITTED", today, DUE, DUE, END, today));
        assertNotNull(Emp201Rules.payProblem("DRAFT", today, DUE, DUE, END, today));
        assertNotNull(Emp201Rules.payProblem("PAID", today, DUE, DUE, END, today));
        assertNotNull(Emp201Rules.payProblem("SUBMITTED", null, DUE, DUE, END, today));
        assertNotNull(Emp201Rules.payProblem("SUBMITTED", today.plusDays(1), DUE, DUE, END, today));
        assertNotNull(Emp201Rules.payProblem("SUBMITTED", END.minusDays(1), DUE, DUE, END, today));
        assertNotNull(Emp201Rules.payProblem("SUBMITTED", today, null, DUE, END, today));
        assertNotNull(Emp201Rules.payProblem("SUBMITTED", today, BigDecimal.ZERO, DUE, END, today));
        assertEquals("The amount can have at most two decimals.", Emp201Rules.payProblem("SUBMITTED", today, new BigDecimal("12345.670"), DUE, END, today));
        assertTrue(Emp201Rules.payProblem("SUBMITTED", today, new BigDecimal("12345.66"), DUE, END, today).contains("12345.67"));
    }

    @Test
    void overdueAndLate() {
        LocalDate due = LocalDate.of(2026, 11, 6);
        assertTrue(Emp201Rules.overdue("DRAFT", due, due.plusDays(1)));
        assertTrue(Emp201Rules.overdue("SUBMITTED", due, due.plusDays(1)));
        assertFalse(Emp201Rules.overdue("SUBMITTED", due, due));
        assertFalse(Emp201Rules.overdue("PAID", due, due.plusDays(30)));
        assertTrue(Emp201Rules.late(due.plusDays(1), due));
        assertFalse(Emp201Rules.late(due, due));
        assertFalse(Emp201Rules.late(null, due));
    }
}
