package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PatientNoteRulesTest {

    @Test void kindIsCaseInsensitiveAndUnknownKindsAreRefused() {
        assertEquals("ALERT", PatientNoteRules.kind(" alert "));
        assertThrows(IllegalArgumentException.class, () -> PatientNoteRules.kind("memo"));
        assertThrows(IllegalArgumentException.class, () -> PatientNoteRules.kind(null));
    }

    @Test void alertsDefaultToWarningAndNotesHaveNoSeverity() {
        assertEquals("WARNING", PatientNoteRules.severity("ALERT", null));
        assertEquals("CRITICAL", PatientNoteRules.severity("ALERT", "critical"));
        assertNull(PatientNoteRules.severity("NOTE", "CRITICAL"));
        assertThrows(IllegalArgumentException.class, () -> PatientNoteRules.severity("ALERT", "LOUD"));
    }

    @Test void bodyIsTrimmedAndBounded() {
        assertEquals("Needs interpreter", PatientNoteRules.body("  Needs interpreter "));
        assertThrows(IllegalArgumentException.class, () -> PatientNoteRules.body("   "));
        assertThrows(IllegalArgumentException.class, () -> PatientNoteRules.body("x".repeat(PatientNoteRules.MAX_BODY + 1)));
        assertEquals(PatientNoteRules.MAX_BODY, PatientNoteRules.body("x".repeat(PatientNoteRules.MAX_BODY)).length());
    }
}
