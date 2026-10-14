package za.co.handyflow.platform.expenses.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import za.co.handyflow.platform.expenses.application.internal.ExpenseLifecycle.Action;

/** Finance patch F7: draft, submit, approve, reject and resubmit, withdraw, reimburse. */
class ExpenseLifecycleTest {

    @Test void draftAndRejectedCanBeEditedAndSubmitted() {
        for (String s : new String[] { "DRAFT", "REJECTED" }) {
            assertNull(ExpenseLifecycle.problem(Action.EDIT, s));
            assertNull(ExpenseLifecycle.problem(Action.SUBMIT, s));
        }
        for (String s : new String[] { "PENDING", "APPROVED", "REIMBURSED", "CANCELLED" }) {
            assertNotNull(ExpenseLifecycle.problem(Action.EDIT, s));
            assertNotNull(ExpenseLifecycle.problem(Action.SUBMIT, s));
        }
    }
    @Test void withdrawBeforeDecisionOnly() {
        assertNull(ExpenseLifecycle.problem(Action.WITHDRAW, "DRAFT"));
        assertNull(ExpenseLifecycle.problem(Action.WITHDRAW, "PENDING"));
        assertTrue(ExpenseLifecycle.problem(Action.WITHDRAW, "APPROVED").endsWith("approved"));
    }
    @Test void decideOnlyPendingAndPayOnlyApproved() {
        assertNull(ExpenseLifecycle.problem(Action.APPROVE, "PENDING"));
        assertNull(ExpenseLifecycle.problem(Action.REJECT, "PENDING"));
        assertNotNull(ExpenseLifecycle.problem(Action.APPROVE, "DRAFT"));
        assertNull(ExpenseLifecycle.problem(Action.REIMBURSE, "APPROVED"));
        assertTrue(ExpenseLifecycle.problem(Action.REIMBURSE, "REIMBURSED").contains("already paid out"));
        assertNotNull(ExpenseLifecycle.problem(Action.REIMBURSE, null));
    }
    @Test void onlyTheClaimant() {
        UUID me = UUID.randomUUID();
        assertNull(ExpenseLifecycle.claimantProblem(me, me));
        assertNotNull(ExpenseLifecycle.claimantProblem(me, UUID.randomUUID()));
        assertNotNull(ExpenseLifecycle.claimantProblem(me, null));
    }
    @Test void completeBeforeSubmitting() {
        LocalDate today = LocalDate.of(2026, 10, 14);
        BigDecimal r = new BigDecimal("120.50");
        assertNull(ExpenseLifecycle.submitProblem("Thabo", today, "FUEL", "Trip to site", r, today));
        assertNotNull(ExpenseLifecycle.submitProblem(" ", today, "FUEL", "x", r, today));
        assertNotNull(ExpenseLifecycle.submitProblem("Thabo", today.plusDays(1), "FUEL", "x", r, today));
        assertNotNull(ExpenseLifecycle.submitProblem("Thabo", today, "FUEL", "x", BigDecimal.ZERO, today));
        assertNotNull(ExpenseLifecycle.submitProblem("Thabo", today, "", "x", r, today));
    }
    @Test void rejectionNeedsAReason() {
        assertNotNull(ExpenseLifecycle.reasonProblem(null));
        assertNotNull(ExpenseLifecycle.reasonProblem("no"));
        assertNull(ExpenseLifecycle.reasonProblem("No receipt attached"));
    }
}
