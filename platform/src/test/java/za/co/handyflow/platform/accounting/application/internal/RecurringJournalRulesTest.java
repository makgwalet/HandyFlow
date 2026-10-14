package za.co.handyflow.platform.accounting.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.accounting.application.internal.RecurringJournalRules.Line;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RecurringJournalRulesTest {

    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID();
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static Line dr(UUID a, String n) { return new Line(a, null, d(n), BigDecimal.ZERO); }
    private static Line cr(UUID a, String n) { return new Line(a, null, BigDecimal.ZERO, d(n)); }

    @Test
    void runDatesDoNotDriftAfterAShortMonth() {
        LocalDate start = LocalDate.of(2026, 1, 31);
        assertEquals(LocalDate.of(2026, 2, 28), RecurringJournalRules.runDate(start, "MONTHLY", 1));
        assertEquals(LocalDate.of(2026, 3, 31), RecurringJournalRules.runDate(start, "MONTHLY", 2));
        assertEquals(LocalDate.of(2026, 4, 30), RecurringJournalRules.runDate(start, "QUARTERLY", 1));
        assertEquals(LocalDate.of(2027, 1, 31), RecurringJournalRules.runDate(start, "ANNUALLY", 1));
    }

    @Test
    void spreadAddsUpExactly() {
        List<BigDecimal> parts = RecurringJournalRules.spread(d("1000.00"), 3);
        assertEquals(List.of(d("333.33"), d("333.33"), d("333.34")), parts);
        assertEquals(0, parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(d("1000.00")));
        assertEquals(List.of(d("120.00")), RecurringJournalRules.spread(d("120.00"), 1));
    }

    @Test
    void prepaymentReleaseDebitsExpenseAndCreditsPrepaid() {
        List<Line> lines = RecurringJournalRules.prepaymentLines(A, B, d("100.00"), "Insurance");
        assertEquals(A, lines.get(0).accountId());
        assertEquals(d("100.00"), lines.get(0).debit());
        assertEquals(d("100.00"), lines.get(1).credit());
        assertNull(RecurringJournalRules.linesProblem(lines));
    }

    @Test
    void accrualReversesOnTheFirstOfNextMonth() {
        assertEquals(LocalDate.of(2026, 11, 1), RecurringJournalRules.reversalDate(LocalDate.of(2026, 10, 31)));
        assertEquals(LocalDate.of(2027, 1, 1), RecurringJournalRules.reversalDate(LocalDate.of(2026, 12, 15)));
        List<Line> rev = RecurringJournalRules.reversal(List.of(dr(A, "50"), cr(B, "50")));
        assertEquals(d("50"), rev.get(0).credit());
        assertEquals(d("50"), rev.get(1).debit());
    }

    @Test
    void checksTheLines() {
        assertNotNull(RecurringJournalRules.linesProblem(List.of(dr(A, "10"))));
        assertNotNull(RecurringJournalRules.linesProblem(List.of(dr(A, "10"), cr(B, "9"))));
        assertNotNull(RecurringJournalRules.linesProblem(List.of(new Line(A, null, d("5"), d("5")), cr(B, "5"))));
        assertNotNull(RecurringJournalRules.linesProblem(List.of(new Line(null, null, d("5"), BigDecimal.ZERO), cr(B, "5"))));
        assertNotNull(RecurringJournalRules.linesProblem(List.of(new Line(A, null, BigDecimal.ZERO, BigDecimal.ZERO), cr(B, "5"))));
        assertNull(RecurringJournalRules.linesProblem(List.of(dr(A, "10"), cr(B, "10"))));
    }

    @Test
    void checksTheScheduleBeforeCreating() {
        LocalDate s = LocalDate.of(2026, 11, 1);
        List<Line> ok = List.of(dr(A, "10"), cr(B, "10"));
        assertNull(RecurringJournalRules.createProblem("Rent", "RECURRING", "MONTHLY", s, null, null, ok, null, null, null));
        assertNotNull(RecurringJournalRules.createProblem(" ", "RECURRING", "MONTHLY", s, null, null, ok, null, null, null));
        assertNotNull(RecurringJournalRules.createProblem("Rent", "WEEKLY", "MONTHLY", s, null, null, ok, null, null, null));
        assertNotNull(RecurringJournalRules.createProblem("Rent", "RECURRING", "DAILY", s, null, null, ok, null, null, null));
        assertNotNull(RecurringJournalRules.createProblem("Rent", "RECURRING", "MONTHLY", s, s.minusDays(1), null, ok, null, null, null));
        assertNotNull(RecurringJournalRules.createProblem("Rent", "RECURRING", "MONTHLY", s, null, null, List.of(dr(A, "10")), null, null, null));
        assertNull(RecurringJournalRules.createProblem("Insurance", "PREPAYMENT", "MONTHLY", s, null, 12, null, d("1200"), A, B));
        assertNotNull(RecurringJournalRules.createProblem("Insurance", "PREPAYMENT", "MONTHLY", s, null, 1, null, d("1200"), A, B));
        assertNotNull(RecurringJournalRules.createProblem("Insurance", "PREPAYMENT", "MONTHLY", s, null, 12, null, d("0"), A, B));
        assertNotNull(RecurringJournalRules.createProblem("Insurance", "PREPAYMENT", "MONTHLY", s, null, 12, null, d("1200"), A, A));
    }

    @Test
    void worksOutWhatIsDue() {
        LocalDate s = LocalDate.of(2026, 8, 1);
        assertEquals(List.of(0, 1, 2), RecurringJournalRules.dueIndexes(s, "MONTHLY", 0, null, null, LocalDate.of(2026, 10, 14)));
        assertEquals(List.of(2), RecurringJournalRules.dueIndexes(s, "MONTHLY", 2, null, null, LocalDate.of(2026, 10, 14)));
        assertEquals(List.of(), RecurringJournalRules.dueIndexes(s, "MONTHLY", 3, null, null, LocalDate.of(2026, 10, 14)));
        // stops at the number of runs and at the end date
        assertEquals(List.of(0, 1), RecurringJournalRules.dueIndexes(s, "MONTHLY", 0, 2, null, LocalDate.of(2027, 1, 1)));
        assertEquals(List.of(0, 1), RecurringJournalRules.dueIndexes(s, "MONTHLY", 0, null, LocalDate.of(2026, 9, 30), LocalDate.of(2027, 1, 1)));
        // an idle schedule is capped
        assertEquals(RecurringJournalRules.MAX_CATCH_UP, RecurringJournalRules.dueIndexes(s, "MONTHLY", 0, null, null, LocalDate.of(2030, 1, 1)).size());
    }

    @Test
    void previewsUpcomingDates() {
        List<LocalDate> next = RecurringJournalRules.upcoming(LocalDate.of(2026, 8, 1), "QUARTERLY", 1, 3, null, 12);
        assertEquals(List.of(LocalDate.of(2026, 11, 1), LocalDate.of(2027, 2, 1)), next);
    }
}
