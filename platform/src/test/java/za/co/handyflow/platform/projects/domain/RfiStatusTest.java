package za.co.handyflow.platform.projects.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.projects.domain.enums.RfiStatus;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.projects.domain.enums.RfiStatus.*;

class RfiStatusTest {

    @Test
    void normalFlow() {
        assertTrue(DRAFT.canTransitionTo(SUBMITTED));
        assertTrue(SUBMITTED.canTransitionTo(RESPONDED));
        assertTrue(RESPONDED.canTransitionTo(CLOSED));
    }

    @Test
    void clarificationSendsItBackToTheResponder() {
        assertTrue(RESPONDED.canTransitionTo(SUBMITTED));
        assertFalse(CLOSED.canTransitionTo(SUBMITTED));
    }

    @Test
    void aClosedRfiCanBeReopened_butOnlyToResponded() {
        assertTrue(CLOSED.canTransitionTo(RESPONDED));
        assertFalse(CLOSED.canTransitionTo(DRAFT));
        assertFalse(CLOSED.canTransitionTo(CANCELLED));    // closing is a decision, not something to cancel away
    }

    @Test
    void cannotSkipSteps() {
        assertFalse(DRAFT.canTransitionTo(RESPONDED));
        assertFalse(DRAFT.canTransitionTo(CLOSED));
        assertFalse(SUBMITTED.canTransitionTo(CLOSED));    // must be answered first
    }

    @Test
    void cancelledIsFinal_andReachableFromAnyOpenState() {
        for (RfiStatus s : new RfiStatus[]{DRAFT, SUBMITTED, RESPONDED}) {
            assertTrue(s.canTransitionTo(CANCELLED), s + " -> CANCELLED");
        }
        for (RfiStatus t : RfiStatus.values()) assertFalse(CANCELLED.canTransitionTo(t), "CANCELLED -> " + t);
        assertTrue(CANCELLED.isTerminal());
    }

    @Test
    void awaitingResponseMeansSubmitted() {
        assertTrue(SUBMITTED.isAwaitingResponse());
        assertFalse(RESPONDED.isAwaitingResponse());
        assertFalse(DRAFT.isAwaitingResponse());
    }
}