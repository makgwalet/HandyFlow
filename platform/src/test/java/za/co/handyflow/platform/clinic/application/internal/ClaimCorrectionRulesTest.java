package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.domain.model.ClinicClaim;
import za.co.handyflow.platform.clinic.domain.model.ClinicClaimLine;
import za.co.handyflow.platform.clinic.dto.billing.CreateClaimRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimCorrectionRulesTest {

    private static ClinicClaim claim() {
        ClinicClaim c = ClinicClaim.create(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Discovery", "123456", "01");
        c.addLine(ClinicClaimLine.of(c.getId(), "CONSULTATION", "0190", null, "J06.9", "Consultation", BigDecimal.ONE, new BigDecimal("480.00"), null, 0));
        c.addLine(ClinicClaimLine.of(c.getId(), "PROCEDURE", "0201", null, "J06.9", "Spirometry", new BigDecimal("2"), new BigDecimal("150.00"), null, 10));
        c.addLine(ClinicClaimLine.of(c.getId(), "MEDICINE", null, "700001", "J06.9", "Amoxicillin", BigDecimal.ONE, new BigDecimal("90.00"), UUID.randomUUID(), 100));
        return c;
    }

    @Test void theReplacementStartsAsACopyOfSchemeDetailsAndTariffLines() {
        CreateClaimRequest r = ClaimCorrectionRules.copyOf(claim());
        assertEquals("Discovery", r.schemeName());
        assertEquals("123456", r.memberNumber());
        assertEquals("01", r.dependentCode());
        assertEquals("0190", r.consultationTariffCode());
        assertEquals(new BigDecimal("480.00"), r.consultationRate());
        assertEquals(1, r.procedures().size(), "medicine lines are rebuilt from the prescriptions, not copied");
        assertEquals("0201", r.procedures().get(0).tariffCode());
        assertEquals(new BigDecimal("2"), r.procedures().get(0).quantity());
    }

    @Test void theCorrectionNoteNamesTheClaimItReplacesAndIsCapped() {
        ClinicClaim c = claim();
        String note = ClaimCorrectionRules.correctionNote(c, "Wrong member number captured");
        assertTrue(note.startsWith("Corrects claim " + c.getId().toString().substring(0, 8)), note);
        assertTrue(note.endsWith("Wrong member number captured"));
        assertEquals(1000, ClaimCorrectionRules.correctionNote(c, "x".repeat(2000)).length());
    }

    @Test void aReplacementRecordsWhichClaimItCorrects() {
        ClinicClaim old = claim(), fresh = claim();
        fresh.correct(old.getId(), "note");
        assertEquals(old.getId(), fresh.getCorrectsClaimId());
        assertEquals("note", fresh.getNotes());
        assertEquals("DRAFT", fresh.getStatus());
    }

    @Test void notesChangeWithoutTouchingTheAmounts() {
        ClinicClaim c = claim();
        BigDecimal gross = c.getGrossAmount();
        c.changeNotes("Phoned the scheme on Tuesday");
        assertEquals("Phoned the scheme on Tuesday", c.getNotes());
        assertEquals(gross, c.getGrossAmount());
    }
}
