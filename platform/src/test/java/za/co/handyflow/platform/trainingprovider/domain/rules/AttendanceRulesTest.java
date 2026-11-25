package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class AttendanceRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 2, 10);

    @Test
    void presentAndLateCountAsAttended() {
        assertTrue(AttendanceRules.attended("PRESENT"));
        assertTrue(AttendanceRules.attended("LATE"));
        assertFalse(AttendanceRules.attended("ABSENT"));
        assertFalse(AttendanceRules.attended("EXCUSED"));
    }

    @Test
    void recordingChecks() {
        assertNull(AttendanceRules.recordProblem("SCHEDULED", TODAY, TODAY, "ACTIVE", "PRESENT", null));
        assertNull(AttendanceRules.recordProblem("SCHEDULED", TODAY.minusDays(3), TODAY, "ACTIVE", "ABSENT", null));
        assertNotNull(AttendanceRules.recordProblem("CANCELLED", TODAY, TODAY, "ACTIVE", "PRESENT", null));
        assertTrue(AttendanceRules.recordProblem("SCHEDULED", TODAY.plusDays(1), TODAY, "ACTIVE", "PRESENT", null).contains("on or after the day"));
        assertTrue(AttendanceRules.recordProblem("SCHEDULED", TODAY, TODAY, "REMOVED", "PRESENT", null).contains("removed"));
        assertNotNull(AttendanceRules.recordProblem("SCHEDULED", TODAY, TODAY, "ACTIVE", "PRSENT", null));
        assertNotNull(AttendanceRules.recordProblem("SCHEDULED", TODAY, TODAY, "ACTIVE", null, null));
    }

    @Test
    void excusedNeedsAWrittenReason() {
        assertNotNull(AttendanceRules.recordProblem("SCHEDULED", TODAY, TODAY, "ACTIVE", "EXCUSED", null));
        assertNotNull(AttendanceRules.recordProblem("SCHEDULED", TODAY, TODAY, "ACTIVE", "EXCUSED", "sick"));
        assertNull(AttendanceRules.recordProblem("SCHEDULED", TODAY, TODAY, "ACTIVE", "EXCUSED", "Medical appointment, note sighted"));
    }

    @Test
    void aCorrectionNeedsAChangeAndAReason() {
        assertNotNull(AttendanceRules.correctionProblem("ABSENT", "ABSENT", null, null, "Recorded in error today"));
        assertNotNull(AttendanceRules.correctionProblem("ABSENT", "PRESENT", null, null, "short"));
        assertNotNull(AttendanceRules.correctionProblem("ABSENT", "PRESENT", null, null, null));
        assertNull(AttendanceRules.correctionProblem("ABSENT", "PRESENT", null, null, "Learner arrived after the register closed"));
        assertNotNull(AttendanceRules.correctionProblem("ABSENT", "MAYBE", null, null, "Learner arrived after the register closed"));
    }

    @Test
    void changingOnlyTheNoteIsACorrectionButBlankAndNullAreTheSame() {
        assertNull(AttendanceRules.correctionProblem("LATE", "LATE", "10 minutes", "25 minutes", "Corrected the minutes late"));
        assertNotNull(AttendanceRules.correctionProblem("LATE", "LATE", null, "  ", "Nothing really changed here"));
    }

    @Test
    void correctingToExcusedStillNeedsTheExcuse() {
        assertNotNull(AttendanceRules.correctionProblem("ABSENT", "EXCUSED", null, null, "Medical note was handed in later"));
        assertNull(AttendanceRules.correctionProblem("ABSENT", "EXCUSED", null, "Medical note sighted on the 12th", "Medical note was handed in later"));
    }

    @Test
    void summaryLeavesOutExcusedAndUnrecordedFromTheRate() {
        AttendanceRules.Summary s = AttendanceRules.summary(10, 6, 2, 1);
        assertEquals(1, s.unrecorded());
        assertEquals(Integer.valueOf(75), s.ratePercent());
        assertNull(AttendanceRules.summary(5, 0, 0, 2).ratePercent());
        assertEquals(3, AttendanceRules.summary(5, 0, 0, 2).unrecorded());
        assertEquals(Integer.valueOf(100), AttendanceRules.summary(2, 2, 0, 0).ratePercent());
        assertEquals(0, AttendanceRules.summary(0, 0, 0, 0).unrecorded());
    }
}
