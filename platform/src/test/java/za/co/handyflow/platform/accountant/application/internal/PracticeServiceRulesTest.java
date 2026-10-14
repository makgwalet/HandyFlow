package za.co.handyflow.platform.accountant.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.accountant.application.internal.EngagementLetterRules.Draft;
import za.co.handyflow.platform.accountant.application.internal.WipRules.Entry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PracticeServiceRulesTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static Draft draft(String basis, String fee) {
        return new Draft("Bookkeeping and VAT", "Monthly bookkeeping\nVAT201 returns", null, basis, fee == null ? null : d(fee), null, LocalDate.of(2026, 11, 1), null);
    }

    @Test
    void aLetterNeedsScopeFeeBasisAndAmountWhereItMatters() {
        assertNull(EngagementLetterRules.draftProblem(draft("FIXED", "1500")));
        assertNull(EngagementLetterRules.draftProblem(draft("HOURLY", null)));
        assertNotNull(EngagementLetterRules.draftProblem(draft("FIXED", null)));
        assertNotNull(EngagementLetterRules.draftProblem(draft("RETAINER", "0")));
        assertNotNull(EngagementLetterRules.draftProblem(draft("WEIRD", "1")));
        assertNotNull(EngagementLetterRules.draftProblem(new Draft("T", " ", null, "HOURLY", null, null, LocalDate.now(), null)));
        assertNotNull(EngagementLetterRules.draftProblem(new Draft("T", "x", null, "HOURLY", null, null, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1))));
    }

    @Test
    void statusMovesOnlyAlongTheAllowedPath() {
        assertNull(EngagementLetterRules.editProblem("DRAFT"));
        assertNotNull(EngagementLetterRules.editProblem("SENT"));
        assertNotNull(EngagementLetterRules.sendProblem("SENT", draft("FIXED", "1")));
        assertNull(EngagementLetterRules.acceptProblem("SENT", "A Client", "EMAIL", TODAY, TODAY));
        assertNotNull(EngagementLetterRules.acceptProblem("DRAFT", "A Client", "EMAIL", TODAY, TODAY));
        assertNotNull(EngagementLetterRules.acceptProblem("SENT", " ", "EMAIL", TODAY, TODAY));
        assertNotNull(EngagementLetterRules.acceptProblem("SENT", "A", "CARRIER_PIGEON", TODAY, TODAY));
        assertNotNull(EngagementLetterRules.acceptProblem("SENT", "A", "EMAIL", TODAY.plusDays(1), TODAY));
        assertNull(EngagementLetterRules.declineProblem("SENT"));
        assertNotNull(EngagementLetterRules.endProblem("SENT"));
        assertNull(EngagementLetterRules.endProblem("ACCEPTED"));
    }

    @Test
    void theLetterNamesFeesPeriodAndExclusions() {
        String t = EngagementLetterRules.render("Smith & Co", "Acme (Pty) Ltd", 2, draft("FIXED", "1500"), TODAY);
        assertTrue(t.contains("14 October 2026"));
        assertTrue(t.contains("(version 2)"));
        assertTrue(t.contains("A fixed fee of R 1500.00"));
        assertTrue(t.contains("from 1 November 2026 until either of us ends it"));
        assertTrue(t.contains("audit, review or assurance"));
        assertTrue(t.contains("VAT201 returns"));
        String h = EngagementLetterRules.render("P", "C", 1, draft("HOURLY", null), TODAY);
        assertTrue(h.contains("standard hourly rates."));
    }

    @Test
    void runsAreAnchoredToTheFirstDate() {
        LocalDate first = LocalDate.of(2026, 1, 31);
        assertEquals(LocalDate.of(2026, 2, 28), ServiceScheduleRules.runDate(first, "MONTHLY", 1));
        assertEquals(LocalDate.of(2026, 3, 31), ServiceScheduleRules.runDate(first, "MONTHLY", 2));
        assertEquals(LocalDate.of(2026, 4, 30), ServiceScheduleRules.runDate(first, "QUARTERLY", 1));
        assertEquals(LocalDate.of(2027, 1, 31), ServiceScheduleRules.runDate(first, "ANNUALLY", 1));
    }

    @Test
    void runsAppearSomeDaysAheadAndCatchUpIsCapped() {
        LocalDate first = LocalDate.of(2026, 10, 19);   // 5 days after today
        assertEquals(List.of(0), ServiceScheduleRules.dueIndexes(first, "MONTHLY", null, -1, TODAY));
        assertEquals(List.of(), ServiceScheduleRules.dueIndexes(LocalDate.of(2026, 10, 20), "MONTHLY", null, -1, TODAY));
        assertEquals(List.of(1), ServiceScheduleRules.dueIndexes(LocalDate.of(2026, 9, 1), "MONTHLY", null, 0, LocalDate.of(2026, 9, 28)));
        assertEquals(12, ServiceScheduleRules.dueIndexes(LocalDate.of(2024, 1, 1), "MONTHLY", null, -1, TODAY).size());
        assertEquals(List.of(0, 1), ServiceScheduleRules.dueIndexes(LocalDate.of(2026, 8, 1), "MONTHLY", LocalDate.of(2026, 9, 15), -1, TODAY));
    }

    @Test
    void scheduleInputsAndRunActions() {
        assertNull(ServiceScheduleRules.problem("BOOKKEEPING", "MONTHLY", TODAY, null, d("1000"), d("5"), 30, null));
        assertNotNull(ServiceScheduleRules.problem("NOPE", "MONTHLY", TODAY, null, null, null, 30, null));
        assertNotNull(ServiceScheduleRules.problem("PAYROLL", "WEEKLY", TODAY, null, null, null, 30, null));
        assertNotNull(ServiceScheduleRules.problem("PAYROLL", "MONTHLY", TODAY, TODAY.minusDays(1), null, null, 30, null));
        assertNull(ServiceScheduleRules.billProblem("DUE", "ACTIVE", d("100")));
        assertNotNull(ServiceScheduleRules.billProblem("BILLED", "ACTIVE", d("100")));
        assertNotNull(ServiceScheduleRules.billProblem("DUE", "ACTIVE", null));
        assertNotNull(ServiceScheduleRules.skipProblem("DUE", " "));
        assertNotNull(ServiceScheduleRules.statusProblem("ENDED", "ACTIVE"));
        assertNotNull(ServiceScheduleRules.statusProblem("PAUSED", "PAUSED"));
        assertNull(ServiceScheduleRules.statusProblem("PAUSED", "ACTIVE"));
        assertEquals("Vat returns, October 2026", ServiceScheduleRules.runDescription("VAT_RETURNS", null, LocalDate.of(2026, 10, 25), "MONTHLY"));
    }

    @Test
    void wipIsAgedByWhenTheTimeWasWorked() {
        var a = WipRules.age(List.of(
                new Entry(TODAY.minusDays(5), d("2"), d("500")),
                new Entry(TODAY.minusDays(45), d("1"), d("500")),
                new Entry(TODAY.minusDays(75), d("1"), d("400")),
                new Entry(TODAY.minusDays(120), d("0.5"), d("400"))), TODAY);
        assertEquals(d("4.5"), a.hours());
        assertEquals(d("2100.00"), a.value());
        assertEquals(d("1000.00"), a.current());
        assertEquals(d("500.00"), a.days31to60());
        assertEquals(d("400.00"), a.days61to90());
        assertEquals(d("200.00"), a.over90());
        assertEquals(TODAY.minusDays(120), a.oldest());
        assertEquals(d("0.00"), WipRules.age(List.of(), TODAY).value().setScale(2));
    }

    @Test
    void realisationAndBudgetUse() {
        assertEquals(d("80.0"), WipRules.realisation(d("800"), d("1000")));
        assertNull(WipRules.realisation(d("800"), d("0")));
        assertEquals(d("120"), WipRules.budgetUsed(d("6"), d("5")));
        assertNull(WipRules.budgetUsed(d("6"), null));
        assertNotNull(WipRules.periodProblem(TODAY, TODAY.minusDays(1)));
        assertNull(WipRules.periodProblem(TODAY.minusDays(30), TODAY));
    }
}
