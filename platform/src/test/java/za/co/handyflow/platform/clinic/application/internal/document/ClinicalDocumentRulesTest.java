package za.co.handyflow.platform.clinic.application.internal.document;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.document.ClinicalDocumentRules.*;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.*;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClinicalDocumentRulesTest {

    static final Letterhead HEAD = new Letterhead("Sample Practice", List.of("Tel 000"), null);
    static final Person PATIENT = new Person("Test Patient", "9001015009087", LocalDate.of(1990, 1, 1), "Female");
    static final Signer SIGNER = new Signer("Dr. Sample Doctor", "HPCSA 0000000");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    static String allText(DocumentModel m) {
        StringBuilder sb = new StringBuilder();
        for (Block b : m.blocks()) {
            if (b instanceof Heading h) sb.append(h.text());
            else if (b instanceof TextBlock t) sb.append(t.text());
            else if (b instanceof KeyValues k) k.pairs().forEach(p -> sb.append(p[0]).append(':').append(p[1]).append(';'));
            else if (b instanceof Signature s) sb.append(s.signerName());
            sb.append('\n');
        }
        return sb.toString();
    }

    @Test void aSickNoteCarriesThePatientTheDatesAndTheSigner() {
        Built b = ClinicalDocumentRules.sickNote(HEAD, PATIENT, SIGNER, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14),
                "Acute bronchitis", "Rest and fluids", TODAY);
        String t = allText(b.model());
        assertTrue(t.contains("Medical certificate"));
        assertTrue(t.contains("ID number:9001015009087"));
        assertTrue(t.contains("examined on 12 October 2026"));
        assertTrue(t.contains("Unfit from:12 October 2026"));
        assertTrue(t.contains("Unfit until:14 October 2026"));
        assertTrue(t.contains("Acute bronchitis"));
        assertTrue(t.contains("Rest and fluids"));
        assertTrue(t.contains("Dr. Sample Doctor"));
        assertEquals("2026-10-14", b.snapshot().get("unfitUntil"));
        assertSame(HEAD, b.model().letterhead());
    }

    @Test void aSickNoteCannotEndBeforeItStarts() {
        assertThrows(IllegalArgumentException.class, () -> ClinicalDocumentRules.sickNote(HEAD, PATIENT, SIGNER, TODAY, TODAY, TODAY.minusDays(1), null, null, TODAY));
        assertDoesNotThrow(() -> ClinicalDocumentRules.sickNote(HEAD, PATIENT, SIGNER, TODAY, TODAY, TODAY, null, null, TODAY));
    }

    @Test void missingDatesAndOptionalTextPrintAsDashesOrAreLeftOut() {
        Built b = ClinicalDocumentRules.sickNote(HEAD, new Person("Test Patient", null, null, null), null, null, null, null, "  ", null, TODAY);
        String t = allText(b.model());
        assertTrue(t.contains("Unfit from:—"));
        assertFalse(t.contains("Diagnosis"));
        assertFalse(t.contains("Notes"));
        assertFalse(t.contains("ID number"));
    }

    @Test void aReferralNamesTheSpecialistTheReasonAndOnlyTheSectionsThatHaveText() {
        Built b = ClinicalDocumentRules.referral(HEAD, PATIENT, SIGNER, "Dr. Specialist", "Cardiology", "Chest pain on exertion", "semi urgent",
                "Chest pain", "Angina?", List.of("I20.9", "R07.4"), null, "  ", TODAY);
        String t = allText(b.model());
        assertTrue(t.contains("Dr. Specialist (Cardiology)"));
        assertTrue(t.contains("Re: Test Patient (DOB 1 January 1990)"));
        assertTrue(t.contains("Urgency: semi urgent"));
        assertTrue(t.contains("Chest pain on exertion"));
        assertTrue(t.contains("I20.9, R07.4"));
        assertFalse(t.contains("Treatment to date"));
        assertFalse(t.contains("Additional notes"));
        assertEquals("SEMI_URGENT", b.snapshot().get("urgency"));
    }

    @Test void aReferralWithoutASpecialistIsAddressedToAColleague() {
        Built b = ClinicalDocumentRules.referral(HEAD, PATIENT, SIGNER, null, null, null, null, null, null, null, null, null, TODAY);
        assertTrue(allText(b.model()).contains("Dear Colleague"));
        assertFalse(allText(b.model()).contains("Urgency"));
    }

    @Test void anUnknownUrgencyIsRefusedRatherThanPrinted() {
        assertThrows(IllegalArgumentException.class, () -> ClinicalDocumentRules.urgency("whenever"));
        assertEquals("ROUTINE", ClinicalDocumentRules.urgency("routine"));
        assertEquals("URGENT", ClinicalDocumentRules.urgency(" URGENT "));
        assertNull(ClinicalDocumentRules.urgency(" "));
    }

    @Test void thePractitionerGetsOneDrNotTwo() {
        assertEquals("Dr. Sarah Mokoena", ClinicalDocumentRules.drName("Sarah Mokoena"));
        assertEquals("Dr Sarah Mokoena", ClinicalDocumentRules.drName("Dr Sarah Mokoena"));
        assertEquals("", ClinicalDocumentRules.drName(null));
        assertEquals("Dr.Sarah Mokoena", ClinicalDocumentRules.drName("Dr.Sarah Mokoena"));
        assertEquals("Dr. Drew Smith", ClinicalDocumentRules.drName("Drew Smith"));   // 0202: "Drew" is a name, not a title
    }

    // ── 0203 ──────────────────────────────────────────────────────────────────

    static final Prescriber PRESCRIBER = new Prescriber("Sarah Mokoena", "MP0000000", "0000000", "General practice");
    static final RxLine AMOX = new RxLine("Sample-medicine 500 mg", 4, "000000-001", "1 capsule", "three times a day", "5 days", "Take with food", 15, 0);

    @Test void aPrescriptionListsTheMedicinesWithDirectionsAndTheValidityLine() {
        Built b = ClinicalDocumentRules.prescription(HEAD, PATIENT, PRESCRIBER, TODAY, List.of(AMOX), true, TODAY);
        String t = allText(b.model());
        assertTrue(t.contains("Prescription"));
        assertTrue(t.contains("Name:Dr. Sarah Mokoena"));
        assertTrue(t.contains("HPCSA no.:MP0000000"));
        assertTrue(t.contains("ID number:9001015009087"));
        assertTrue(t.contains(ClinicalDocumentRules.PRESCRIPTION_VALIDITY));
        assertFalse(t.contains("NOT ISSUED"));
        var grid = b.model().blocks().stream().filter(x -> x instanceof Grid).map(x -> (Grid) x).findFirst().orElseThrow();
        assertEquals(List.of("1", "Sample-medicine 500 mg (Sch 4) · NAPPI 000000-001", "1 capsule  three times a day  for 5 days. Take with food", "15", "0"), grid.rows().get(0));
        assertFalse(t.contains("Dispensed"), "whether it was dispensed changes after issue and is not on the script");
    }

    @Test void anUnsignedVisitsPrescriptionSaysItIsNotIssued() {
        String t = allText(ClinicalDocumentRules.prescription(HEAD, PATIENT, null, TODAY, List.of(), false, TODAY).model());
        assertTrue(t.contains(ClinicalDocumentRules.NOT_SIGNED));
        assertTrue(t.contains("No medicines prescribed for this consultation."));
    }

    @Test void thePrescriptionSnapshotIgnoresTheDateItIsPrintedButNotTheMedicines() {
        var a = ClinicalDocumentRules.prescription(HEAD, PATIENT, PRESCRIBER, TODAY, List.of(AMOX), true, TODAY).snapshot();
        var later = ClinicalDocumentRules.prescription(HEAD, PATIENT, PRESCRIBER, TODAY, List.of(AMOX), true, TODAY.plusDays(3)).snapshot();
        assertEquals(a, later);
        var more = new RxLine("Sample-medicine 500 mg", 4, "000000-001", "1 capsule", "three times a day", "5 days", "Take with food", 21, 0);
        assertNotEquals(a, ClinicalDocumentRules.prescription(HEAD, PATIENT, PRESCRIBER, TODAY, List.of(more), true, TODAY).snapshot());
    }

    @Test void aVisitSummaryHasTheSectionsThatHaveTextAndItsAddenda() {
        var vitals = new Vitals("72.5 kg", null, "120/80", "70 bpm", null, null);
        var add = List.of(new Addendum("Correction", "13 Oct 2026 10:00", "Nurse Sample", "Weight was 72.5, not 75."));
        Built b = ClinicalDocumentRules.visitSummary(HEAD, PATIENT, "Dr Sarah Mokoena", TODAY, vitals, "Cough", "Acute bronchitis", List.of("J20.9"),
                "Rest", 7, add, true, TODAY);
        String t = allText(b.model());
        assertTrue(t.contains("Practitioner:Dr Sarah Mokoena"));
        assertTrue(t.contains("ICD-10 codes"));
        assertTrue(t.contains("Follow-up recommended in 7 days."));
        assertTrue(t.contains("Correction · 13 Oct 2026 10:00 · Nurse Sample"));
        assertTrue(t.contains(ClinicalDocumentRules.SUMMARY_NOTE));
        var grid = b.model().blocks().stream().filter(x -> x instanceof Grid).map(x -> (Grid) x).findFirst().orElseThrow();
        assertEquals(List.of("72.5 kg", "—", "120/80", "70 bpm", "—", "—"), grid.rows().get(0));
        var noAddendum = ClinicalDocumentRules.visitSummary(HEAD, PATIENT, "Dr Sarah Mokoena", TODAY, vitals, "Cough", "Acute bronchitis", List.of("J20.9"),
                "Rest", 7, List.of(), true, TODAY).snapshot();
        assertNotEquals(noAddendum, b.snapshot(), "an addendum changes the summary, so it is replaced");
    }

    @Test void aVisitSummaryWithoutVitalsHasNoVitalsTable() {
        Built b = ClinicalDocumentRules.visitSummary(HEAD, PATIENT, null, TODAY, new Vitals(null, null, " ", null, null, null), null, null, null, null, null, null, true, TODAY);
        assertTrue(b.model().blocks().stream().noneMatch(x -> x instanceof Grid));
        assertFalse(allText(b.model()).contains("Practitioner"));
    }

    @Test void aConsentRecordStatesTheDecisionInPlainWordsWithSignatureLines() {
        var fact = new ConsentFact("MEDICAL_AID_SHARING", "REVOKED", "WRITTEN", "Reception Sample", "13 October 2026 09:15", "Patient asked in writing");
        Built b = ClinicalDocumentRules.consentRecord(HEAD, PATIENT, fact, TODAY);
        String t = allText(b.model());
        assertTrue(t.contains("Record of consent"));
        assertTrue(t.contains("Consent for:Sharing information with the medical aid"));
        assertTrue(t.contains("Decision:Withdrawn"));
        assertTrue(t.contains("How it was given:Written"));
        assertTrue(t.contains("Recorded on:13 October 2026 09:15"));
        assertTrue(t.contains("Patient or legal guardian"));
        assertEquals(2, b.model().blocks().stream().filter(x -> x instanceof Signature).count());
        assertEquals("Given", ClinicalDocumentRules.decisionLabel("granted"));
        assertEquals("Not recorded", ClinicalDocumentRules.methodLabel(null));
        assertEquals("other kind", ClinicalDocumentRules.consentLabel("OTHER_KIND"));
    }
}
