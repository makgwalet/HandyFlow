package za.co.handyflow.platform.training.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TrainingBookingRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final LocalDate LATER = TODAY.plusDays(10);

    private static String book(String emp, String session, boolean open, LocalDate start, boolean already, Integer cap, long live) {
        return TrainingBookingRules.bookProblem(emp, session, open, start, TODAY, already, cap, live);
    }

    @Test
    void anOpenSessionWithRoomMayBeBooked() {
        assertNull(book("ACTIVE", "SCHEDULED", true, LATER, false, 10, 9));
        assertNull(book("ACTIVE", "SCHEDULED", true, LATER, false, null, 500));
        assertNull(book("ACTIVE", "SCHEDULED", true, TODAY, false, 10, 0));
    }

    @Test
    void aSessionNotOpenedForSelfBookingIsRefused() {
        assertTrue(book("ACTIVE", "SCHEDULED", false, LATER, false, 10, 0).contains("not open"));
    }

    @Test
    void refusesFullStartedCancelledAndDoubleBookings() {
        assertEquals("This session is full.", book("ACTIVE", "SCHEDULED", true, LATER, false, 10, 10));
        assertEquals("This session has already started.", book("ACTIVE", "SCHEDULED", true, TODAY.minusDays(1), false, 10, 0));
        assertEquals("You are already booked on this session.", book("ACTIVE", "SCHEDULED", true, LATER, true, 10, 3));
        assertEquals("This session is no longer open for booking.", book("ACTIVE", "CANCELLED", true, LATER, false, 10, 0));
        assertEquals("This session is no longer open for booking.", book("ACTIVE", "IN_PROGRESS", true, LATER, false, 10, 0));
    }

    @Test
    void someoneWhoHasLeftOrIsSuspendedCannotBook() {
        assertEquals("Only active employees can book training.", book("TERMINATED", "SCHEDULED", true, LATER, false, 10, 0));
    }

    @Test
    void aPersonMayCancelOwnPlaceUntilTheDayTheSessionStarts() {
        assertNull(TrainingBookingRules.cancelProblem("ENROLLED", "SCHEDULED", TODAY.plusDays(1), TODAY));
        assertNotNull(TrainingBookingRules.cancelProblem("ENROLLED", "SCHEDULED", TODAY, TODAY));
        assertNotNull(TrainingBookingRules.cancelProblem("ENROLLED", "IN_PROGRESS", TODAY.plusDays(1), TODAY));
        assertNotNull(TrainingBookingRules.cancelProblem("ATTENDED", "SCHEDULED", LATER, TODAY));
        assertNotNull(TrainingBookingRules.cancelProblem("CANCELLED", "SCHEDULED", LATER, TODAY));
    }

    @Test
    void wordsTheManagersNotice() {
        assertEquals("Thandi Nkosi booked themselves onto \"First Aid\" starting 2026-11-05.", TrainingBookingRules.managerBookedMessage("Thandi Nkosi", "First Aid", LocalDate.of(2026, 11, 5)));
        assertTrue(TrainingBookingRules.managerCancelledMessage("Thandi Nkosi", "First Aid", null).endsWith("\"First Aid\"."));
    }
}
