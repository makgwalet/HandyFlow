package za.co.handyflow.platform.payrollbureau.domain.rules;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RunLifecycleRulesTest {

    private static void ok(String from, String to) {
        assertTrue(RunLifecycleRules.transitionProblem(from, to) == null, from + " -> " + to);
    }

    private static void refused(String from, String to) {
        assertTrue(RunLifecycleRules.transitionProblem(from, to) != null, from + " -> " + to);
    }

    @Test
    void theForwardPathIsAllowed() {
        ok("DRAFT", "CALCULATED");
        ok("CALCULATED", "APPROVED");
        ok("APPROVED", "FINALISED");
        ok("FINALISED", "REVERSED");
    }

    @Test
    void aRunCanBeReopenedUntilItIsFinal() {
        ok("CALCULATED", "DRAFT");
        ok("APPROVED", "DRAFT");
        refused("FINALISED", "DRAFT");
        refused("REVERSED", "DRAFT");
    }

    @Test
    void stepsCannotBeSkippedOrRepeated() {
        refused("DRAFT", "APPROVED");
        refused("DRAFT", "FINALISED");
        refused("CALCULATED", "FINALISED");
        refused("CALCULATED", "CALCULATED");
        refused("APPROVED", "REVERSED");
        refused("DRAFT", "REVERSED");
        refused("REVERSED", "FINALISED");
        refused("FINALISED", "APPROVED");
    }

    @Test
    void anUnknownStatusIsNeverTreatedAsAllowed() {
        refused("PROCESSED", "FINALISED");
        refused("SOMETHING", "DRAFT");
    }

    @Test
    void theUserWhoCalculatedCannotApprove() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(RunLifecycleRules.approvalProblem(a, a) != null);
        assertTrue(RunLifecycleRules.approvalProblem(a, b) == null);
        assertTrue(RunLifecycleRules.approvalProblem(null, b) == null);   // runs calculated before this existed
        assertTrue(RunLifecycleRules.approvalProblem(a, null) != null);
    }

    @Test
    void aReversalNeedsAReason() {
        assertTrue(RunLifecycleRules.reversalProblem(null) != null);
        assertTrue(RunLifecycleRules.reversalProblem("   ") != null);
        assertTrue(RunLifecycleRules.reversalProblem("Wrong pay period used") == null);
        assertTrue(RunLifecycleRules.reversalProblem("x".repeat(501)) != null);
    }

    @Test
    void onlyAFinalisedRunsPayslipsAreSent() {
        assertTrue(RunLifecycleRules.payslipsCanBeSent("FINALISED"));
        for (String s : new String[]{"DRAFT", "CALCULATED", "APPROVED", "REVERSED"}) assertFalse(RunLifecycleRules.payslipsCanBeSent(s), s);
    }

    @Test
    void reversedAndDraftRunsDoNotCountTowardsAMonthsUif() {
        assertTrue(RunLifecycleRules.countsTowardsMonth("CALCULATED"));
        assertTrue(RunLifecycleRules.countsTowardsMonth("FINALISED"));
        assertFalse(RunLifecycleRules.countsTowardsMonth("REVERSED"));
        assertFalse(RunLifecycleRules.countsTowardsMonth("DRAFT"));
    }
}
