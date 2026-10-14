package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DisputeRulesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static final String TEXT = "Customer says the goods never arrived";

    @Test
    void responseIsDueTenBusinessDaysLater() {
        assertEquals(LocalDate.of(2026, 10, 23), DisputeRules.responseDue(LocalDate.of(2026, 10, 9), Set.of()));
    }

    @Test
    void opensOnAnActiveCase() {
        assertNull(DisputeRules.openProblem(CaseStatus.OPEN, "AMOUNT", TEXT, true, d("100"), d("500"), true));
        assertNull(DisputeRules.openProblem(CaseStatus.DISPUTED, "OTHER", TEXT, false, null, null, false));
        assertNull(DisputeRules.openProblem(CaseStatus.HANDED_TO_LEGAL, "NOT_OWED", TEXT, false, null, null, false));
    }

    @Test
    void refusesOnFinishedCases() {
        for (CaseStatus s : new CaseStatus[]{CaseStatus.CLOSED, CaseStatus.SETTLED, CaseStatus.WRITTEN_OFF}) {
            assertNotNull(DisputeRules.openProblem(s, "AMOUNT", TEXT, false, null, null, false));
        }
    }

    @Test
    void refusesBadInput() {
        assertNotNull(DisputeRules.openProblem(CaseStatus.OPEN, "BOGUS", TEXT, false, null, null, false));
        assertNotNull(DisputeRules.openProblem(CaseStatus.OPEN, "AMOUNT", "short", false, null, null, false));
        assertNotNull(DisputeRules.openProblem(CaseStatus.OPEN, "AMOUNT", TEXT, false, null, null, true));
        assertNotNull(DisputeRules.openProblem(CaseStatus.OPEN, "AMOUNT", TEXT, true, d("0"), null, true));
        assertNotNull(DisputeRules.openProblem(CaseStatus.OPEN, "AMOUNT", TEXT, true, d("600"), d("500"), true));
    }

    @Test
    void pausesTheCaseExceptWhenWithLegal() {
        assertEquals(CaseStatus.DISPUTED, DisputeRules.statusAfterOpen(CaseStatus.DEMAND_SENT));
        assertEquals(CaseStatus.HANDED_TO_LEGAL, DisputeRules.statusAfterOpen(CaseStatus.HANDED_TO_LEGAL));
        assertEquals(CaseStatus.DEMAND_SENT, DisputeRules.statusToRemember(CaseStatus.DEMAND_SENT));
        assertNull(DisputeRules.statusToRemember(CaseStatus.DISPUTED));
        assertNull(DisputeRules.statusToRemember(CaseStatus.HANDED_TO_LEGAL));
    }

    @Test
    void restoresTheCaseWhenTheLastDisputeIsResolved() {
        assertEquals(CaseStatus.DEMAND_SENT, DisputeRules.statusAfterResolve(CaseStatus.DISPUTED, CaseStatus.DEMAND_SENT, false));
        assertEquals(CaseStatus.DISPUTED, DisputeRules.statusAfterResolve(CaseStatus.DISPUTED, CaseStatus.DEMAND_SENT, true));
        assertEquals(CaseStatus.OPEN, DisputeRules.statusAfterResolve(CaseStatus.DISPUTED, null, false));
        assertEquals(CaseStatus.OPEN, DisputeRules.statusAfterResolve(CaseStatus.DISPUTED, CaseStatus.CLOSED, false));
        // someone moved the case on by hand meanwhile: leave it
        assertEquals(CaseStatus.PAYMENT_PLAN_ACTIVE, DisputeRules.statusAfterResolve(CaseStatus.PAYMENT_PLAN_ACTIVE, CaseStatus.OPEN, false));
        assertEquals(CaseStatus.HANDED_TO_LEGAL, DisputeRules.statusAfterResolve(CaseStatus.HANDED_TO_LEGAL, null, false));
    }

    @Test
    void resolutionNeedsAnOutcomeAndAnExplanation() {
        assertNotNull(DisputeRules.resolveProblem("OPEN", null, "Checked with the branch", null, null));
        assertNotNull(DisputeRules.resolveProblem("OPEN", "REJECTED", "no", null, null));
        assertNull(DisputeRules.resolveProblem("OPEN", "REJECTED", "Delivery note signed by the debtor", null, d("100")));
        assertNull(DisputeRules.resolveProblem("UNDER_REVIEW", "WITHDRAWN", "Debtor withdrew by phone", d("0"), null));
        assertNotNull(DisputeRules.resolveProblem("RESOLVED", "REJECTED", "Delivery note signed by the debtor", null, null));
    }

    @Test
    void concessionsNeedAnAmountWithinTheDispute() {
        assertNotNull(DisputeRules.resolveProblem("OPEN", "UPHELD", "Credit agreed with the debtor", null, d("100")));
        assertNull(DisputeRules.resolveProblem("OPEN", "UPHELD", "Credit agreed with the debtor", d("100"), d("100")));
        assertNotNull(DisputeRules.resolveProblem("OPEN", "UPHELD", "Credit agreed with the debtor", d("150"), d("100")));
        assertNull(DisputeRules.resolveProblem("OPEN", "PARTLY_UPHELD", "Half the goods were short", d("40"), d("100")));
        assertNotNull(DisputeRules.resolveProblem("OPEN", "PARTLY_UPHELD", "Half the goods were short", d("100"), d("100")));
        assertNotNull(DisputeRules.resolveProblem("OPEN", "REJECTED", "Delivery note signed by the debtor", d("10"), d("100")));
    }

    @Test
    void onlyOpenDisputesAreTakenIntoReview() {
        assertNull(DisputeRules.takeOnProblem("OPEN"));
        assertNotNull(DisputeRules.takeOnProblem("UNDER_REVIEW"));
    }

    @Test
    void overdueMeansPastDueAndNotResolved() {
        LocalDate due = LocalDate.of(2026, 10, 23);
        assertFalse(DisputeRules.overdue("OPEN", due, due));
        assertTrue(DisputeRules.overdue("OPEN", due, due.plusDays(1)));
        assertFalse(DisputeRules.overdue("RESOLVED", due, due.plusDays(30)));
    }
}
