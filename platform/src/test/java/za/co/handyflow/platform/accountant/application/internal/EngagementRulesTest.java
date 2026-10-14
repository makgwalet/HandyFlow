package za.co.handyflow.platform.accountant.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.UUID;

/** Finance patch F15: review and sign-off of a client's financial year. */
class EngagementRulesTest {
    UUID prep = UUID.randomUUID(), rev = UUID.randomUUID(), partner = UUID.randomUUID();

    @Test void financialYearIsNamedByTheYearItEndsIn() {
        // February year end: March 2025 to February 2026 is FY 2026
        assertEquals(2026, EngagementRules.financialYearOf(2, 2025, 3));
        assertEquals(2026, EngagementRules.financialYearOf(2, 2026, 2));
        assertEquals(2027, EngagementRules.financialYearOf(2, 2026, 3));
        // December year end is the calendar year
        assertEquals(2026, EngagementRules.financialYearOf(12, 2026, 1));
        assertEquals(2026, EngagementRules.financialYearOf(12, 2026, 12));
    }

    @Test void yearRange() {
        assertEquals(LocalDate.of(2025, 3, 1), EngagementRules.start(2, 2026));
        assertEquals(LocalDate.of(2026, 2, 28), EngagementRules.end(2, 2026));
        assertEquals(LocalDate.of(2028, 2, 29), EngagementRules.end(2, 2028));
        assertEquals(LocalDate.of(2026, 1, 1), EngagementRules.start(12, 2026));
        assertEquals(LocalDate.of(2026, 12, 31), EngagementRules.end(12, 2026));
    }

    @Test void prepareNeedsAnOpenYearWithEverythingPosted() {
        assertNull(EngagementRules.prepareProblem("OPEN", 0));
        assertTrue(EngagementRules.prepareProblem("OPEN", 2).contains("2 journals"));
        assertTrue(EngagementRules.prepareProblem("OPEN", 1).contains("1 journal in this year is"));
        assertNotNull(EngagementRules.prepareProblem("PREPARED", 0));
    }

    @Test void reviewerIsNotThePreparer() {
        assertNull(EngagementRules.reviewProblem("PREPARED", prep, rev));
        assertNotNull(EngagementRules.reviewProblem("PREPARED", prep, prep));
        assertNotNull(EngagementRules.reviewProblem("OPEN", prep, rev));
        assertNull(EngagementRules.reviewProblem("PREPARED", null, rev));
    }

    @Test void signOffNeedsAReviewedYearAndNotThePreparer() {
        assertNull(EngagementRules.signOffProblem("REVIEWED", prep, partner, true));
        assertNotNull(EngagementRules.signOffProblem("REVIEWED", prep, prep, true));
        assertNotNull(EngagementRules.signOffProblem("PREPARED", prep, partner, true));
        // a one-person practice may sign its own work, and it is flagged
        assertNull(EngagementRules.signOffProblem("REVIEWED", prep, prep, false));
        assertTrue(EngagementRules.soleSigner(prep, prep, false));
        assertFalse(EngagementRules.soleSigner(prep, prep, true));
        assertFalse(EngagementRules.soleSigner(prep, partner, false));
    }

    @Test void reopeningNeedsAReason() {
        assertNotNull(EngagementRules.reopenProblem("OPEN", "a very good reason"));
        assertNotNull(EngagementRules.reopenProblem("SIGNED_OFF", "short"));
        assertNotNull(EngagementRules.reopenProblem("SIGNED_OFF", null));
        assertNull(EngagementRules.reopenProblem("SIGNED_OFF", "Client sent late invoices"));
        assertNull(EngagementRules.reopenProblem("REVIEWED", "Reviewer found an error"));
    }

    @Test void onlyASignedOffYearLocksJournals() {
        assertTrue(EngagementRules.locksJournals("SIGNED_OFF"));
        assertFalse(EngagementRules.locksJournals("REVIEWED"));
        assertFalse(EngagementRules.locksJournals("OPEN"));
    }

    @Test void warnings() {
        assertTrue(EngagementRules.warnings(0, true, 0).isEmpty());
        assertEquals(3, EngagementRules.warnings(2, false, 1).size());
        assertTrue(EngagementRules.warnings(1, true, 0).get(0).contains("1 tax deadline is"));
    }
}
