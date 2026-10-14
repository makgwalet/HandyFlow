package za.co.handyflow.platform.accounting.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;

/** Finance patch F5: the books' lock date. */
class PeriodLockRulesTest {
    LocalDate sep30 = LocalDate.of(2026, 9, 30), oct1 = LocalDate.of(2026, 10, 1), today = LocalDate.of(2026, 10, 14);

    @Test void nothingLockedAllowsAnyDate() { assertNull(PeriodLockRules.postProblem(LocalDate.of(2020, 1, 1), null)); }
    @Test void lockDateItselfIsClosed() {
        String p = PeriodLockRules.postProblem(sep30, sep30);
        assertNotNull(p);
        assertTrue(p.contains("2026-10-01"));
        assertNotNull(PeriodLockRules.postProblem(LocalDate.of(2026, 1, 5), sep30));
        assertNull(PeriodLockRules.postProblem(oct1, sep30));
    }
    @Test void lockMovesForwardOnlyAndNotPastToday() {
        assertNull(PeriodLockRules.lockProblem(sep30, null, today, 0));
        assertNull(PeriodLockRules.lockProblem(oct1, sep30, today, 0));
        assertNotNull(PeriodLockRules.lockProblem(sep30, sep30, today, 0));
        assertNotNull(PeriodLockRules.lockProblem(LocalDate.of(2026, 8, 31), sep30, today, 0));
        assertNotNull(PeriodLockRules.lockProblem(today.plusDays(1), null, today, 0));
        assertNull(PeriodLockRules.lockProblem(today, null, today, 0));
        assertNotNull(PeriodLockRules.lockProblem(null, null, today, 0));
    }
    @Test void draftsInThePeriodBlockClosing() {
        assertTrue(PeriodLockRules.lockProblem(sep30, null, today, 1).startsWith("1 draft journal is"));
        assertTrue(PeriodLockRules.lockProblem(sep30, null, today, 3).startsWith("3 draft journals are"));
    }
    @Test void reopenMovesBackWithAReason() {
        String why = "Late supplier invoice for September";
        assertNull(PeriodLockRules.reopenProblem(LocalDate.of(2026, 8, 31), sep30, why));
        assertNull(PeriodLockRules.reopenProblem(null, sep30, why));
        assertNotNull(PeriodLockRules.reopenProblem(sep30, sep30, why));
        assertNotNull(PeriodLockRules.reopenProblem(oct1, sep30, why));
        assertNotNull(PeriodLockRules.reopenProblem(null, null, why));
        assertNotNull(PeriodLockRules.reopenProblem(null, sep30, "late"));
        assertNotNull(PeriodLockRules.reopenProblem(null, sep30, "x".repeat(501)));
    }
}
