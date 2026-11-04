package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalProgressResponse.Step;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FmPortalProgressTest {

    private static final Instant T = Instant.parse("2026-11-01T08:00:00Z");

    private static List<String> states(List<Step> s) { return s.stream().map(Step::state).toList(); }

    @Test
    void aNewRequestIsReportedAndWaitingForTheTeam() {
        var s = FmPortalProgress.steps("REQUESTED", T, null, null, null);
        assertEquals(List.of("DONE", "CURRENT", "PENDING", "PENDING", "PENDING"), states(s));
    }

    @Test
    void anAssignedJobIsBooked() {
        var s = FmPortalProgress.steps("ASSIGNED", T, T, LocalDate.of(2026, 11, 3), null);
        assertEquals(List.of("DONE", "DONE", "CURRENT", "PENDING", "PENDING"), states(s));
        assertNotNull(s.get(1).at());
    }

    @Test
    void pendingVerificationLooksLikeWorkUnderWayAndShowsNoCompletionDate() {
        var s = FmPortalProgress.steps("PENDING_VERIFICATION", T, T, null, T);
        assertEquals(List.of("DONE", "DONE", "DONE", "CURRENT", "PENDING"), states(s));
        assertNull(s.get(4).at());
    }

    @Test
    void aCompletedJobIsDoneThroughout() {
        var s = FmPortalProgress.steps("COMPLETED", T, T, null, T);
        assertEquals(List.of("DONE", "DONE", "DONE", "DONE", "DONE"), states(s));
        assertEquals(T, s.get(4).at());
    }

    @Test
    void onHoldIsFlaggedOnTheBookedStep() {
        var s = FmPortalProgress.steps("ON_HOLD", T, T, null, null);
        assertEquals("On hold for now", s.get(2).note());
    }

    @Test
    void declinedAndCancelledStopTheJourney() {
        assertEquals(2, FmPortalProgress.steps("REJECTED", T, null, null, null).size());
        assertEquals("CANCELLED", FmPortalProgress.steps("CANCELLED", T, null, null, null).get(1).key());
    }

    @Test
    void onlyPhotosAreVisibleAndAfterPhotosWaitForCompletion() {
        assertTrue(FmPortalProgress.photoVisible("PHOTO", "image/jpeg", "ASSIGNED"));
        assertTrue(FmPortalProgress.photoVisible("BEFORE_PHOTO", "image/png", "IN_PROGRESS"));
        assertFalse(FmPortalProgress.photoVisible("AFTER_PHOTO", "image/png", "PENDING_VERIFICATION"));
        assertTrue(FmPortalProgress.photoVisible("AFTER_PHOTO", "image/png", "COMPLETED"));
        assertFalse(FmPortalProgress.photoVisible("CERTIFICATE", "image/png", "COMPLETED"));
        assertFalse(FmPortalProgress.photoVisible("DOCUMENT", "application/pdf", "COMPLETED"));
        assertFalse(FmPortalProgress.photoVisible("PHOTO", "application/pdf", "COMPLETED"));
        assertFalse(FmPortalProgress.photoVisible(null, "image/png", "COMPLETED"));
    }
}
