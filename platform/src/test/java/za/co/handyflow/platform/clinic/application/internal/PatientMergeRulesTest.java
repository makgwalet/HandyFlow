package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.PatientMergeRules.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PatientMergeRulesTest {
    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID();
    private static Side side(UUID id, String first, String last, String idNo, LocalDate dob) {
        return new Side(id, true, false, null, "P000001", first, last, idNo, dob, null, false, 0);
    }
    private static Side with(Side s, boolean archived, UUID mergedInto, boolean restricted, int open) {
        return new Side(s.id(), s.found(), archived, mergedInto, s.patientNumber(), s.firstName(), s.lastName(), s.idNumber(), s.dateOfBirth(), s.sexAtBirth(), restricted, open);
    }
    private final Side s = side(A, "Thandi", "Nkosi", "8001015009087", LocalDate.of(1980, 1, 1));
    private final Side o = side(B, "Thandi", "Nkosi", "8001015009087", LocalDate.of(1980, 1, 1));

    @Test void aCleanPairHasNoBlockers() { assertEquals(List.of(), PatientMergeRules.blockers(s, o)); }
    @Test void samePatientTwiceIsRefused() { assertEquals(1, PatientMergeRules.blockers(s, s).size()); }
    @Test void aMissingRecordIsRefused() {
        Side gone = new Side(B, false, false, null, null, null, null, null, null, null, false, 0);
        assertTrue(PatientMergeRules.blockers(s, gone).get(0).contains("not found"));
    }
    @Test void anArchivedOrAlreadyMergedRecordIsRefused() {
        assertTrue(PatientMergeRules.blockers(s, with(o, true, UUID.randomUUID(), false, 0)).get(0).contains("already merged"));
        assertTrue(PatientMergeRules.blockers(with(s, true, null, false, 0), o).get(0).contains("archived"));
    }
    @Test void restrictedRecordsAreRefused() { assertTrue(PatientMergeRules.blockers(s, with(o, false, null, true, 0)).get(0).contains("restricted")); }
    @Test void openVisitsOnTheMergedAwayRecordBlock() {
        assertTrue(PatientMergeRules.blockers(s, with(o, false, null, false, 2)).get(0).contains("2 visits still open"));
        assertTrue(PatientMergeRules.blockers(s, with(o, false, null, false, 1)).get(0).contains("1 visit still open"));
    }
    @Test void theSurvivorsOwnOpenVisitsDoNotBlock() { assertEquals(List.of(), PatientMergeRules.blockers(with(s, false, null, false, 3), o)); }

    @Test void identicalRecordsHaveNoConflicts() { assertEquals(List.of(), PatientMergeRules.conflicts(s, o)); }
    @Test void differentIdNumbersAndBirthDatesBlockUntilAcknowledged() {
        Side other = side(B, "Thandi", "Nkosi", "9001015009088", LocalDate.of(1990, 1, 1));
        var c = PatientMergeRules.conflicts(s, other);
        assertEquals(2, c.size());
        assertTrue(PatientMergeRules.needsAcknowledgement(c));
    }
    @Test void aMissingIdNumberIsNotAConflict() {
        assertEquals(List.of(), PatientMergeRules.conflicts(s, side(B, "Thandi", "Nkosi", null, null)));
        assertEquals(List.of(), PatientMergeRules.conflicts(s, side(B, "Thandi", "Nkosi", " 8001 015009087 ", null)));
    }
    @Test void differentNamesWarnButDoNotBlock() {
        var c = PatientMergeRules.conflicts(s, side(B, "Thandiwe", "Nkosi", "8001015009087", LocalDate.of(1980, 1, 1)));
        assertEquals(1, c.size());
        assertFalse(PatientMergeRules.needsAcknowledgement(c));
    }
    @Test void nameCaseAndSpacesDoNotMatter() { assertEquals(List.of(), PatientMergeRules.conflicts(s, side(B, " thandi ", "NKOSI", "8001015009087", LocalDate.of(1980, 1, 1)))); }

    @Test void onlyBlanksOnTheSurvivorAreFilled() {
        Map<String, String> sv = new HashMap<>(); sv.put("phone", "0821234567"); sv.put("email", " ");
        Map<String, String> ot = new HashMap<>(); ot.put("phone", "0830000000"); ot.put("email", "t@x.co.za"); ot.put("blood_type", "O+"); ot.put("gender", "");
        assertEquals(List.of("email", "blood_type"), PatientMergeRules.fill(sv, ot).stream().sorted(Comparator.comparingInt(PatientMergeRules.FILLABLE::indexOf)).toList());
    }
    @Test void nameIsNeverFilledOrOverwritten() { assertFalse(PatientMergeRules.FILLABLE.contains("first_name") || PatientMergeRules.FILLABLE.contains("last_name")); }

    @Test void trailTablesStayAndSingleRowTablesKeepTheSurvivors() {
        assertEquals(PatientMergeRules.Handling.SKIP, PatientMergeRules.handling("clinic_access_log"));
        assertEquals(PatientMergeRules.Handling.SKIP, PatientMergeRules.handling("clinic_patient_corrections"));
        assertEquals(PatientMergeRules.Handling.SINGLE, PatientMergeRules.handling("clinic_patient_profile"));
        assertEquals(PatientMergeRules.Handling.MOVE, PatientMergeRules.handling("clinic_consultations"));
        assertEquals(PatientMergeRules.Handling.MOVE, PatientMergeRules.handling("clinic_something_new"));
    }
    @Test void aReasonIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> PatientMergeRules.reason(" no "));
        assertThrows(IllegalArgumentException.class, () -> PatientMergeRules.reason(null));
        assertEquals("Same person, registered twice", PatientMergeRules.reason("  Same person, registered twice "));
    }
    @Test void onlyPlainTableNamesReachSql() {
        assertTrue(PatientMergeRules.safeName("clinic_claims"));
        assertFalse(PatientMergeRules.safeName("clinic_claims; drop table x"));
        assertFalse(PatientMergeRules.safeName("Clinic"));
        assertFalse(PatientMergeRules.safeName(null));
    }
    @Test void labelsAreReadable() {
        assertEquals("Visits", PatientMergeRules.label("clinic_consultations"));
        assertEquals("Some new thing", PatientMergeRules.label("clinic_some_new_thing"));
        assertEquals("Merged into P000001 Thandi Nkosi", PatientMergeRules.archiveReason("P000001", "Thandi Nkosi"));
    }
}
