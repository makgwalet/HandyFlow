package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LostReportRulesTest {
    private static final Instant T = Instant.parse("2026-11-20T10:00:00Z");

    @Test void dueIs24HoursAfterReport() { assertEquals(Instant.parse("2026-11-21T10:00:00Z"), LostReportRules.dueAt(T)); }

    @Test void notOverdueBeforeDeadline() { assertFalse(LostReportRules.overdue(T.plusSeconds(3600), LostReportRules.dueAt(T), null)); }
    @Test void overdueAfterDeadlineWithoutCase() { assertTrue(LostReportRules.overdue(T.plusSeconds(86401), LostReportRules.dueAt(T), " ")); }
    @Test void caseNumberStopsTheClock() { assertFalse(LostReportRules.overdue(T.plusSeconds(999999), LostReportRules.dueAt(T), "CAS 123/11/2026")); }
    @Test void noDeadlineIsNeverOverdue() { assertFalse(LostReportRules.overdue(T, null, null)); }

    @Test void caseIsTrimmedAndBlankBecomesNull() {
        assertEquals("CAS 1", LostReportRules.normaliseCase("  CAS 1 "));
        assertNull(LostReportRules.normaliseCase("   "));
        assertNull(LostReportRules.normaliseCase(null));
    }
    @Test void overlongCaseRejected() {
        assertThrows(IllegalArgumentException.class, () -> LostReportRules.normaliseCase("x".repeat(61)));
        assertEquals(60, LostReportRules.normaliseCase("x".repeat(60)).length());
    }
}
