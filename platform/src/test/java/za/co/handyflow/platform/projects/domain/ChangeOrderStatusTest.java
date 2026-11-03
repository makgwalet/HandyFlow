package za.co.handyflow.platform.projects.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.projects.domain.enums.ChangeOrderStatus;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.projects.domain.enums.ChangeOrderStatus.*;

class ChangeOrderStatusTest {

    @Test
    void allowedTransitions() {
        assertTrue(DRAFT.canTransitionTo(SUBMITTED));
        assertTrue(DRAFT.canTransitionTo(WITHDRAWN));
        assertTrue(SUBMITTED.canTransitionTo(APPROVED));
        assertTrue(SUBMITTED.canTransitionTo(REJECTED));
        assertTrue(SUBMITTED.canTransitionTo(WITHDRAWN));
        assertTrue(REJECTED.canTransitionTo(DRAFT));      // reopened for a new revision
    }

    @Test
    void approvedAndWithdrawnAreFinal() {
        for (ChangeOrderStatus t : ChangeOrderStatus.values()) {
            assertFalse(APPROVED.canTransitionTo(t), "APPROVED -> " + t);
            assertFalse(WITHDRAWN.canTransitionTo(t), "WITHDRAWN -> " + t);
        }
        assertTrue(APPROVED.isTerminal());
        assertTrue(WITHDRAWN.isTerminal());
    }

    @Test
    void cannotSkipSteps() {
        assertFalse(DRAFT.canTransitionTo(APPROVED));
        assertFalse(DRAFT.canTransitionTo(REJECTED));
        assertFalse(REJECTED.canTransitionTo(SUBMITTED));   // must be reopened to DRAFT first
        assertFalse(REJECTED.canTransitionTo(APPROVED));
        assertFalse(SUBMITTED.canTransitionTo(DRAFT));
    }

    @Test
    void onlyDraftIsEditable() {
        assertTrue(DRAFT.isEditable());
        for (ChangeOrderStatus s : new ChangeOrderStatus[]{SUBMITTED, APPROVED, REJECTED, WITHDRAWN}) {
            assertFalse(s.isEditable(), s + " must not be editable");
        }
    }
}