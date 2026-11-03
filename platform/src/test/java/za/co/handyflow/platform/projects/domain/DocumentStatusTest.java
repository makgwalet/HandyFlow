package za.co.handyflow.platform.projects.domain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.projects.domain.enums.DocumentStatus;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.projects.domain.enums.DocumentStatus.*;

class DocumentStatusTest {

    @Test
    void reviewPath() {
        assertTrue(CURRENT.canTransitionTo(FOR_REVIEW));
        assertTrue(FOR_REVIEW.canTransitionTo(APPROVED));
        assertTrue(FOR_REVIEW.canTransitionTo(REJECTED));
        assertTrue(APPROVED.canTransitionTo(SUPERSEDED));
        assertTrue(REJECTED.canTransitionTo(SUPERSEDED));
    }

    @Test
    void cannotApproveWithoutReview() {
        assertFalse(CURRENT.canTransitionTo(APPROVED));     // the old code allowed this
        assertFalse(DRAFT.canTransitionTo(APPROVED));
        assertFalse(CURRENT.canTransitionTo(REJECTED));
    }

    @Test
    void reviewedDocumentsCanNeverBeVoided_theyAreTheRecord() {
        assertFalse(APPROVED.canTransitionTo(VOIDED));
        assertFalse(REJECTED.canTransitionTo(VOIDED));
        assertFalse(FOR_REVIEW.canTransitionTo(VOIDED));
        assertFalse(SUPERSEDED.canTransitionTo(VOIDED));
    }

    @Test
    void onlyUnreviewedDocumentsCanBeVoided() {
        assertTrue(CURRENT.canTransitionTo(VOIDED));
        assertTrue(DRAFT.canTransitionTo(VOIDED));
    }

    @Test
    void supersededAndVoidedAreFinal() {
        for (DocumentStatus t : DocumentStatus.values()) {
            assertFalse(SUPERSEDED.canTransitionTo(t), "SUPERSEDED -> " + t);
            assertFalse(VOIDED.canTransitionTo(t), "VOIDED -> " + t);
        }
        assertTrue(SUPERSEDED.isFinal());
        assertTrue(VOIDED.isFinal());
    }

    @Test
    void approvedRevisionIsNotRetiredByUploadingANewOne() {
        assertFalse(APPROVED.supersededOnUpload());      // stays in force until the new one is approved
        assertTrue(CURRENT.supersededOnUpload());
        assertTrue(FOR_REVIEW.supersededOnUpload());
        assertTrue(REJECTED.supersededOnUpload());
    }

    @Test
    void liveAndTransmittable() {
        assertTrue(CURRENT.isLive());
        assertTrue(FOR_REVIEW.isLive());
        assertTrue(APPROVED.isLive());
        assertFalse(REJECTED.isLive());
        assertFalse(SUPERSEDED.canBeTransmitted());
        assertFalse(VOIDED.canBeTransmitted());
        assertTrue(APPROVED.canBeTransmitted());
    }
}