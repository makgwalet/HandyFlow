package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentRulesTest {

    private static final Instant TEN = Instant.parse("2026-10-07T08:00:00Z"); // 10:00 Johannesburg

    @Test
    void overlappingBookingsClash() {
        assertTrue(AppointmentRules.overlaps(TEN, 30, TEN.plusSeconds(15 * 60), 30));
        assertTrue(AppointmentRules.overlaps(TEN, 30, TEN, 30));
        assertTrue(AppointmentRules.overlaps(TEN, 60, TEN.plusSeconds(10 * 60), 10)); // one inside the other
    }

    @Test
    void backToBackBookingsDoNotClash() {
        assertFalse(AppointmentRules.overlaps(TEN, 30, TEN.plusSeconds(30 * 60), 30));
        assertFalse(AppointmentRules.overlaps(TEN.plusSeconds(30 * 60), 30, TEN, 30));
    }

    @Test
    void lengthDefaultsAndIsBounded() {
        assertEquals(30, AppointmentRules.minutes(null));
        assertEquals(5, AppointmentRules.minutes(5));
        assertEquals(480, AppointmentRules.minutes(480));
        assertThrows(IllegalArgumentException.class, () -> AppointmentRules.minutes(0));
        assertThrows(IllegalArgumentException.class, () -> AppointmentRules.minutes(4));
        assertThrows(IllegalArgumentException.class, () -> AppointmentRules.minutes(481));
    }

    @Test
    void walkInGraceIsFifteenMinutes() {
        assertFalse(AppointmentRules.inThePast(TEN.minusSeconds(14 * 60), TEN));
        assertFalse(AppointmentRules.inThePast(TEN.minusSeconds(15 * 60), TEN));
        assertTrue(AppointmentRules.inThePast(TEN.minusSeconds(16 * 60), TEN));
        assertFalse(AppointmentRules.inThePast(TEN.plusSeconds(60), TEN));
    }

    @Test
    void conflictMessageNamesTheTimeInClinicTimeAndTheOtherPatient() {
        var one = List.of(new AppointmentRules.Clash("Sam Nkosi", TEN, 30));
        assertEquals("Dr Lee already has an appointment 10:00–10:30 with Sam Nkosi.",
                AppointmentRules.conflictMessage("Dr Lee", one, AppointmentRules.CLINIC_ZONE));
        var two = List.of(new AppointmentRules.Clash("Sam Nkosi", TEN, 30), new AppointmentRules.Clash("Ann", TEN.plusSeconds(1800), 30));
        assertEquals("This practitioner already has an appointment 10:00–10:30 with Sam Nkosi (and 1 more).",
                AppointmentRules.conflictMessage(null, two, AppointmentRules.CLINIC_ZONE));
    }

    @Test
    void patientConflictMessageNamesThePractitionerWhenThereIsOne() {
        var one = List.of(new AppointmentRules.Clash("Dr Lee", TEN, 30));
        assertEquals("Jane Dlamini already has an appointment 10:00–10:30 with Dr Lee.",
                AppointmentRules.patientConflictMessage("Jane Dlamini", one, AppointmentRules.CLINIC_ZONE));
        var none = List.of(new AppointmentRules.Clash(null, TEN, 30), new AppointmentRules.Clash("Dr Lee", TEN.plusSeconds(1800), 30));
        assertEquals("This patient already has an appointment 10:00–10:30 (and 1 more).",
                AppointmentRules.patientConflictMessage(null, none, AppointmentRules.CLINIC_ZONE));
    }

    @Test
    void roomConflictMessageNamesTheRoomAndWhoIsIn() {
        var one = List.of(new AppointmentRules.Clash("Sam Nkosi", TEN, 30));
        assertEquals("Room 2 is already booked 10:00–10:30 for Sam Nkosi.",
                AppointmentRules.roomConflictMessage("Room 2", one, AppointmentRules.CLINIC_ZONE));
        var two = List.of(new AppointmentRules.Clash("Sam Nkosi", TEN, 30), new AppointmentRules.Clash("Ann", TEN.plusSeconds(1800), 30));
        assertEquals("This room is already booked 10:00–10:30 for Sam Nkosi (and 1 more).",
                AppointmentRules.roomConflictMessage(null, two, AppointmentRules.CLINIC_ZONE));
    }
}
