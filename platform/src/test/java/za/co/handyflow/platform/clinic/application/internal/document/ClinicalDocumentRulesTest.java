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
    }
}
