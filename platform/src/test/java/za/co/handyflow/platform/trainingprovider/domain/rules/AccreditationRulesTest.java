package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.trainingprovider.domain.rules.AccreditationRules.Outcome;
import za.co.handyflow.platform.trainingprovider.domain.rules.AccreditationRules.ScopeFacts;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccreditationRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final UUID SITE_A = UUID.randomUUID();
    private static final UUID SITE_B = UUID.randomUUID();
    private static final UUID MAKER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private static String problem(String from, String to, String reason, String number, LocalDate approved,
                                  LocalDate expiry, String conditions, String source, UUID actor, String override) {
        return AccreditationRules.transitionProblem(from, to, reason, number, approved, expiry, conditions, source,
                actor, MAKER, override, TODAY);
    }

    @Test
    void submittedApplicationIsNotAnApproval() {
        ScopeFacts s = new ScopeFacts("SUBMITTED", null, "ACTIVE", null, null, SITE_A);
        AccreditationRules.Coverage c = AccreditationRules.evaluate(s, SITE_A, TODAY);
        assertFalse(c.covered());
        assertEquals(Outcome.NOT_APPROVED, c.outcome());
    }

    @Test
    void approvedAtOneSiteDoesNotCoverAnother() {
        ScopeFacts s = new ScopeFacts("APPROVED", TODAY.plusYears(1), "ACTIVE", null, null, SITE_A);
        assertTrue(AccreditationRules.evaluate(s, SITE_A, TODAY).covered());
        AccreditationRules.Coverage other = AccreditationRules.evaluate(s, SITE_B, TODAY);
        assertFalse(other.covered());
        assertEquals(Outcome.SITE_NOT_COVERED, other.outcome());
    }

    @Test
    void noSiteGivenIsNeverCovered() {
        ScopeFacts s = new ScopeFacts("APPROVED", null, "ACTIVE", null, null, SITE_A);
        assertFalse(AccreditationRules.evaluate(s, null, TODAY).covered());
    }

    @Test
    void passedExpiryIsExpiredEvenIfStoredAsApproved() {
        assertEquals("EXPIRED", AccreditationRules.effectiveStatus("APPROVED", TODAY.minusDays(1), TODAY));
        assertEquals("APPROVED", AccreditationRules.effectiveStatus("APPROVED", TODAY, TODAY));
        ScopeFacts s = new ScopeFacts("APPROVED", TODAY.minusDays(1), "ACTIVE", null, null, SITE_A);
        assertEquals(Outcome.EXPIRED, AccreditationRules.evaluate(s, SITE_A, TODAY).outcome());
    }

    @Test
    void renewalPendingCoversOnlyUntilTheCurrentExpiry() {
        ScopeFacts s = new ScopeFacts("RENEWAL_PENDING", TODAY.plusDays(10), "ACTIVE", null, null, SITE_A);
        assertEquals(Outcome.COVERED_RENEWAL_PENDING, AccreditationRules.evaluate(s, SITE_A, TODAY).outcome());
        assertEquals(Outcome.EXPIRED, AccreditationRules.evaluate(s, SITE_A, TODAY.plusDays(11)).outcome());
    }

    @Test
    void conditionalApprovalCoversButSaysSo() {
        ScopeFacts s = new ScopeFacts("CONDITIONAL_APPROVAL", null, "ACTIVE", null, null, SITE_A);
        AccreditationRules.Coverage c = AccreditationRules.evaluate(s, SITE_A, TODAY);
        assertTrue(c.covered());
        assertEquals(Outcome.COVERED_WITH_CONDITIONS, c.outcome());
    }

    @Test
    void suspendedAndWithdrawnScopeDoNotCover() {
        assertEquals(Outcome.SUSPENDED,
                AccreditationRules.evaluate(new ScopeFacts("SUSPENDED", null, "ACTIVE", null, null, SITE_A), SITE_A, TODAY).outcome());
        assertEquals(Outcome.SCOPE_WITHDRAWN,
                AccreditationRules.evaluate(new ScopeFacts("APPROVED", null, "WITHDRAWN", null, null, SITE_A), SITE_A, TODAY).outcome());
    }

    @Test
    void scopeDatesAreRespected() {
        ScopeFacts s = new ScopeFacts("APPROVED", null, "ACTIVE", TODAY.plusDays(5), TODAY.plusDays(50), SITE_A);
        assertEquals(Outcome.SCOPE_NOT_IN_FORCE, AccreditationRules.evaluate(s, SITE_A, TODAY).outcome());
        assertTrue(AccreditationRules.evaluate(s, SITE_A, TODAY.plusDays(6)).covered());
        assertEquals(Outcome.SCOPE_NOT_IN_FORCE, AccreditationRules.evaluate(s, SITE_A, TODAY.plusDays(51)).outcome());
    }

    @Test
    void bestAnswerAcrossScopes() {
        assertEquals(Outcome.NO_SCOPE, AccreditationRules.best(List.of(), SITE_A, TODAY).outcome());
        ScopeFacts lapsed = new ScopeFacts("APPROVED", TODAY.minusDays(30), "ACTIVE", null, null, SITE_A);
        ScopeFacts live = new ScopeFacts("APPROVED", TODAY.plusDays(30), "ACTIVE", null, null, SITE_A);
        ScopeFacts elsewhere = new ScopeFacts("APPROVED", TODAY.plusDays(30), "ACTIVE", null, null, SITE_B);
        assertEquals(Outcome.COVERED, AccreditationRules.best(List.of(lapsed, live), SITE_A, TODAY).outcome());
        assertEquals(Outcome.EXPIRED, AccreditationRules.best(List.of(lapsed, elsewhere), SITE_A, TODAY).outcome());
        assertEquals(Outcome.SITE_NOT_COVERED, AccreditationRules.best(List.of(elsewhere), SITE_A, TODAY).outcome());
    }

    @Test
    void lifecycleOnlyMovesAlongAllowedSteps() {
        assertNotNull(problem("DRAFT", "SUBMITTED", null, null, null, null, null, null, OTHER, null));
        assertNull(problem("DRAFT", "APPLICATION_PREPARATION", null, null, null, null, null, null, OTHER, null));
        assertNotNull(problem("REJECTED", "APPROVED", null, "A1", TODAY, null, null, "Authority letter", OTHER, null));
        assertNotNull(problem("WITHDRAWN", "DRAFT", null, null, null, null, null, null, OTHER, null));
        assertNotNull(problem("SUBMITTED", "NOT_A_STATUS", null, null, null, null, null, null, OTHER, null));
    }

    @Test
    void refusalsAndSuspensionsNeedAReason() {
        assertNotNull(problem("UNDER_REVIEW", "REJECTED", "no", null, null, null, null, null, OTHER, null));
        assertNull(problem("UNDER_REVIEW", "REJECTED", "Authority declined the application", null, null, null, null, null, OTHER, null));
        assertNotNull(problem("APPROVED", "SUSPENDED", "   ", null, null, null, null, null, OTHER, null));
    }

    @Test
    void approvalNeedsNumberDateAndVerificationSource() {
        assertNotNull(problem("UNDER_REVIEW", "APPROVED", null, null, TODAY, null, null, "Authority letter", OTHER, null));
        assertNotNull(problem("UNDER_REVIEW", "APPROVED", null, "ACC-1", null, null, null, "Authority letter", OTHER, null));
        assertNotNull(problem("UNDER_REVIEW", "APPROVED", null, "ACC-1", TODAY, null, null, "", OTHER, null));
        assertNotNull(problem("UNDER_REVIEW", "APPROVED", null, "ACC-1", TODAY.plusDays(1), null, null, "Authority letter", OTHER, null));
        assertNotNull(problem("UNDER_REVIEW", "APPROVED", null, "ACC-1", TODAY, TODAY.minusDays(1), null, "Authority letter", OTHER, null));
        assertNull(problem("UNDER_REVIEW", "APPROVED", null, "ACC-1", TODAY, TODAY.plusYears(5), null, "Authority letter", OTHER, null));
    }

    @Test
    void alreadyExpiredApprovalCannotBeRecordedAsCurrent() {
        assertNotNull(problem("DRAFT", "APPROVED", null, "ACC-1", TODAY.minusYears(6), TODAY.minusDays(1), null, "Authority letter", OTHER, null));
    }

    @Test
    void conditionalApprovalNeedsConditionsWritten() {
        assertNotNull(problem("UNDER_REVIEW", "CONDITIONAL_APPROVAL", null, "ACC-1", TODAY, null, "short", "Authority letter", OTHER, null));
        assertNull(problem("UNDER_REVIEW", "CONDITIONAL_APPROVAL", null, "ACC-1", TODAY, null,
                "Submit assessor registrations within 60 days", "Authority letter", OTHER, null));
    }

    @Test
    void whoEnteredTheRecordNeedsAReasonToApproveIt() {
        assertNotNull(problem("UNDER_REVIEW", "APPROVED", null, "ACC-1", TODAY, null, null, "Authority letter", MAKER, null));
        assertNotNull(problem("UNDER_REVIEW", "APPROVED", null, "ACC-1", TODAY, null, null, "Authority letter", MAKER, "only me"));
        assertNull(problem("UNDER_REVIEW", "APPROVED", null, "ACC-1", TODAY, null, null, "Authority letter", MAKER,
                "Sole owner, no second person available"));
        assertNull(problem("UNDER_REVIEW", "APPROVED", null, "ACC-1", TODAY, null, null, "Authority letter", OTHER, null));
    }

    @Test
    void detailsLockOnceApproved() {
        assertTrue(AccreditationRules.detailsEditable("DRAFT"));
        assertTrue(AccreditationRules.detailsEditable("UNDER_REVIEW"));
        assertFalse(AccreditationRules.detailsEditable("APPROVED"));
        assertFalse(AccreditationRules.detailsEditable("SUSPENDED"));
        assertFalse(AccreditationRules.detailsEditable("WITHDRAWN"));
    }
}
