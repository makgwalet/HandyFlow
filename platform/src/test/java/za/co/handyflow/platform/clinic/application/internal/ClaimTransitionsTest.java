package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClaimTransitionsTest {

    @Test
    void theNormalLifecycleIsAllowed() {
        assertDoesNotThrow(() -> ClaimTransitions.require("ACCEPT", "SUBMITTED"));
        assertDoesNotThrow(() -> ClaimTransitions.require("PAID", "ACCEPTED"));
        assertDoesNotThrow(() -> ClaimTransitions.require("PARTIAL", "ACCEPTED"));
        assertDoesNotThrow(() -> ClaimTransitions.require("REJECT", "SUBMITTED"));
        assertDoesNotThrow(() -> ClaimTransitions.require("REJECT", "ACCEPTED"));
        assertDoesNotThrow(() -> ClaimTransitions.require("PAID", "PARTIAL"));
    }

    @Test
    void aClaimCannotSkipSteps() {
        assertThrows(IllegalStateException.class, () -> ClaimTransitions.require("PAID", "DRAFT"));
        assertThrows(IllegalStateException.class, () -> ClaimTransitions.require("PAID", "SUBMITTED"));
        assertThrows(IllegalStateException.class, () -> ClaimTransitions.require("ACCEPT", "DRAFT"));
        assertThrows(IllegalStateException.class, () -> ClaimTransitions.require("PARTIAL", "SUBMITTED"));
    }

    @Test
    void aFinishedClaimCannotBeReopenedOrChanged() {
        assertThrows(IllegalStateException.class, () -> ClaimTransitions.require("REJECT", "PAID"));
        assertThrows(IllegalStateException.class, () -> ClaimTransitions.require("ACCEPT", "REJECTED"));
        assertThrows(IllegalStateException.class, () -> ClaimTransitions.require("PAID", "PAID"));
        assertThrows(IllegalStateException.class, () -> ClaimTransitions.require("PARTIAL", "PARTIAL"));
    }

    @Test
    void theMessageNamesTheStatusAndWhatIsAllowed() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ClaimTransitions.require("PAID", "DRAFT"));
        assertTrue(e.getMessage().contains("DRAFT"));
        assertTrue(e.getMessage().contains("ACCEPTED"));
    }

    @Test
    void unknownActionsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> ClaimTransitions.require("VOID", "DRAFT"));
    }
}
