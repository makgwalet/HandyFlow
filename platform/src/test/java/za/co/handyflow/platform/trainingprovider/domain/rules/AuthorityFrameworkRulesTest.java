package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.trainingprovider.domain.rules.AccreditationRules.Outcome;
import za.co.handyflow.platform.trainingprovider.domain.rules.AccreditationRules.ScopeFacts;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthorityFrameworkRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final UUID SITE = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void authoritiesAreListedInTheAgreedOrder() {
        assertEquals(List.of("QCTO", "SETA", "SAQA", "DHET", "UMALUSI", "CHE"), AuthorityFrameworkRules.PRIORITY.subList(0, 6));
        assertTrue(AuthorityFrameworkRules.rank("QCTO") < AuthorityFrameworkRules.rank("SETA"));
        assertTrue(AuthorityFrameworkRules.rank("DHET") < AuthorityFrameworkRules.rank("UMALUSI"));
        assertTrue(AuthorityFrameworkRules.rank("CHE") < AuthorityFrameworkRules.rank("OTHER"));
        assertEquals(AuthorityFrameworkRules.PRIORITY.size(), AuthorityFrameworkRules.rank("SOMETHING_NEW"));
    }

    @Test
    void transitionalAndHistoricalNeedANote() {
        assertNull(AuthorityFrameworkRules.currencyProblem("CURRENT", null, null, null, A, null, TODAY));
        assertNotNull(AuthorityFrameworkRules.currencyProblem("TRANSITIONAL", "short", null, null, A, null, TODAY));
        assertNull(AuthorityFrameworkRules.currencyProblem("TRANSITIONAL", "Running under the authority's transition arrangement", null, null, A, null, TODAY));
        assertNotNull(AuthorityFrameworkRules.currencyProblem("OTHER", "x", null, null, A, null, TODAY));
    }

    @Test
    void historicalNeedsAnEndDateThatHasHappenedAndIsAfterApproval() {
        String note = "Replaced by the new registration";
        assertNotNull(AuthorityFrameworkRules.currencyProblem("HISTORICAL", note, null, null, A, null, TODAY));
        assertNotNull(AuthorityFrameworkRules.currencyProblem("HISTORICAL", note, TODAY.plusDays(1), null, A, null, TODAY));
        assertNotNull(AuthorityFrameworkRules.currencyProblem("HISTORICAL", note, TODAY.minusYears(2), null, A, TODAY.minusYears(1), TODAY));
        assertNotNull(AuthorityFrameworkRules.currencyProblem("HISTORICAL", note, TODAY.minusDays(1), A, A, null, TODAY));
        assertNull(AuthorityFrameworkRules.currencyProblem("HISTORICAL", note, TODAY.minusDays(1), B, A, TODAY.minusYears(1), TODAY));
    }

    @Test
    void aSourceIsOptionalButCompleteIfGiven() {
        assertNull(AuthorityFrameworkRules.sourceProblem(null, null, null, null, TODAY));
        assertNotNull(AuthorityFrameworkRules.sourceProblem(null, "Letter of 3 March", null, null, TODAY));
        assertNotNull(AuthorityFrameworkRules.sourceProblem("LETTER", "abc", null, null, TODAY));
        assertNotNull(AuthorityFrameworkRules.sourceProblem("LETTER", "Letter ref 22/3", TODAY.plusDays(1), null, TODAY));
        assertNull(AuthorityFrameworkRules.sourceProblem("LETTER", "Letter ref 22/3", TODAY.minusDays(3), "page 2", TODAY));
    }

    @Test
    void datesMustAgree() {
        assertNotNull(AuthorityFrameworkRules.datesProblem(TODAY, null, TODAY.minusDays(1), null));
        assertNotNull(AuthorityFrameworkRules.datesProblem(TODAY, null, null, TODAY.minusDays(1)));
        assertNotNull(AuthorityFrameworkRules.datesProblem(null, TODAY, null, TODAY.minusDays(1)));
        assertNull(AuthorityFrameworkRules.datesProblem(TODAY, null, TODAY.plusYears(1), TODAY.plusMonths(6)));
    }

    @Test
    void evidenceIsCheckedWhenAdded() {
        assertNotNull(AuthorityFrameworkRules.evidenceProblem("NOPE", "Approval letter", TODAY, null, TODAY));
        assertNotNull(AuthorityFrameworkRules.evidenceProblem("LETTER", "ab", TODAY, null, TODAY));
        assertNotNull(AuthorityFrameworkRules.evidenceProblem("LETTER", "Approval letter", TODAY.plusDays(1), null, TODAY));
        assertNotNull(AuthorityFrameworkRules.evidenceProblem("LETTER", "Approval letter", TODAY, TODAY.plusDays(1), TODAY));
        assertNull(AuthorityFrameworkRules.evidenceProblem("LETTER", "Approval letter", TODAY, TODAY.minusDays(2), TODAY));
    }

    @Test
    void aSecondPersonVerifiesOrTheSamePersonSaysWhy() {
        assertNull(AuthorityFrameworkRules.evidenceDecisionProblem("PENDING", "VERIFIED", A, B, null));
        assertNotNull(AuthorityFrameworkRules.evidenceDecisionProblem("PENDING", "VERIFIED", A, A, null));
        assertNull(AuthorityFrameworkRules.evidenceDecisionProblem("PENDING", "VERIFIED", A, A, "Only person in the office today"));
        assertNotNull(AuthorityFrameworkRules.evidenceDecisionProblem("PENDING", "REJECTED", A, B, "no"));
        assertNull(AuthorityFrameworkRules.evidenceDecisionProblem("PENDING", "REJECTED", A, B, "Letter is not from the authority"));
        assertNotNull(AuthorityFrameworkRules.evidenceDecisionProblem("REJECTED", "VERIFIED", A, B, null));
        assertNull(AuthorityFrameworkRules.evidenceDecisionProblem("VERIFIED", "WITHDRAWN", A, B, "Superseded by a newer letter"));
    }

    @Test
    void flagsAreOnlyPrompts() {
        assertEquals(List.of("NO_VERIFIED_EVIDENCE"), AuthorityFrameworkRules.flags("APPROVED", "CURRENT", 0, 0, null, TODAY));
        assertEquals(List.of(), AuthorityFrameworkRules.flags("APPROVED", "CURRENT", 1, 0, null, TODAY));
        assertEquals(List.of("EXPIRED", "REVIEW_OVERDUE"), AuthorityFrameworkRules.flags("EXPIRED", "CURRENT", 0, 0, TODAY.minusDays(1), TODAY));
        assertTrue(AuthorityFrameworkRules.flags("APPROVED", "TRANSITIONAL", 1, 1, null, TODAY).containsAll(List.of("TRANSITIONAL", "EVIDENCE_PENDING")));
        assertFalse(AuthorityFrameworkRules.flags("APPROVED", "HISTORICAL", 1, 0, TODAY.minusDays(5), TODAY).contains("REVIEW_OVERDUE"));
        assertEquals(List.of(), AuthorityFrameworkRules.flags("DRAFT", "CURRENT", 0, 0, null, TODAY));
    }

    @Test
    void aTransitionalApprovalIsCoveredWithACaveatNeverPlainlyCovered() {
        ScopeFacts s = new ScopeFacts("APPROVED", null, "ACTIVE", null, null, SITE, "TRANSITIONAL", null, null);
        AccreditationRules.Coverage c = AccreditationRules.evaluate(s, SITE, TODAY);
        assertEquals(Outcome.COVERED_WITH_CONDITIONS, c.outcome());
        assertTrue(c.covered());
    }

    @Test
    void aHistoricalApprovalCoversOnlyDaysBeforeItEnded() {
        ScopeFacts s = new ScopeFacts("APPROVED", null, "ACTIVE", null, null, SITE, "HISTORICAL", null, TODAY.minusDays(10));
        assertEquals(Outcome.COVERED, AccreditationRules.evaluate(s, SITE, TODAY.minusDays(20)).outcome());
        assertEquals(Outcome.SCOPE_NOT_IN_FORCE, AccreditationRules.evaluate(s, SITE, TODAY).outcome());
    }

    @Test
    void anApprovalDoesNotCoverDaysBeforeItTookEffect() {
        ScopeFacts s = new ScopeFacts("APPROVED", null, "ACTIVE", null, null, SITE, "CURRENT", TODAY.minusDays(5), null);
        assertEquals(Outcome.SCOPE_NOT_IN_FORCE, AccreditationRules.evaluate(s, SITE, TODAY.minusDays(6)).outcome());
        assertEquals(Outcome.COVERED, AccreditationRules.evaluate(s, SITE, TODAY).outcome());
    }
}
