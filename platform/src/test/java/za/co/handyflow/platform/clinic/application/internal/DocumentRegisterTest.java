package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatientDocument;
import za.co.handyflow.platform.clinic.dto.DocumentDtos.RegisterItem;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DocumentRegisterTest {

    @Test
    void onlySignedAndLockedVisitsProduceDocuments() {
        assertTrue(DocumentRegister.hasRecordDocuments("SIGNED"));
        assertTrue(DocumentRegister.hasRecordDocuments("LOCKED"));
        assertFalse(DocumentRegister.hasRecordDocuments("DRAFT"));
        assertFalse(DocumentRegister.hasRecordDocuments(null));
    }

    @Test
    void aVisitGivesASummaryAndAPrescriptionOnlyWhenThereAreMedicines() {
        UUID c = UUID.randomUUID();
        Instant at = Instant.parse("2026-10-07T22:30:00Z");   // 00:30 on the 8th in South Africa
        List<RegisterItem> none = DocumentRegister.forVisit(c, at, " Cough ", false);
        assertEquals(1, none.size());
        assertEquals("Visit summary: Cough", none.get(0).title());
        assertEquals(LocalDate.of(2026, 10, 8), none.get(0).date());
        assertEquals("/api/v1/clinic/consultations/" + c + "/summary-pdf", none.get(0).downloadPath());
        assertFalse(none.get(0).removable());
        List<RegisterItem> both = DocumentRegister.forVisit(c, at, null, true);
        assertEquals(List.of("VISIT_SUMMARY", "PRESCRIPTION"), both.stream().map(RegisterItem::docType).toList());
        assertEquals("Visit summary", both.get(0).title());
    }

    @Test
    void aStoredEntryPointsAtItsOwnFileAndCanBeRemoved() {
        UUID p = UUID.randomUUID();
        var d = ClinicPatientDocument.create(TenantId.of(UUID.randomUUID()), p, null, "LETTER", "UPLOADED", "Letter", LocalDate.of(2026, 9, 1),
                null, "key", "letter.pdf", "application/pdf", 1234, null);
        RegisterItem i = DocumentRegister.stored(d, "Sister Zodwa", p);
        assertEquals("/api/v1/clinic/patients/" + p + "/documents/" + d.getId() + "/file", i.downloadPath());
        assertTrue(i.removable());
        assertEquals("Sister Zodwa", i.addedBy());
    }

    @Test
    void labReportsUseTheReferenceAndOrderIsNewestFirst() {
        UUID l = UUID.randomUUID();
        RegisterItem lab = DocumentRegister.labReport(l, Instant.parse("2026-09-01T08:00:00Z"), "LAB-77", null);
        assertEquals("Lab report: LAB-77", lab.title());
        assertEquals("lab-result.pdf", lab.fileName());
        assertEquals("/api/v1/clinic/lab/results/" + l + "/pdf", lab.downloadPath());
        RegisterItem later = DocumentRegister.labReport(UUID.randomUUID(), Instant.parse("2026-10-01T08:00:00Z"), null, "r.pdf");
        RegisterItem undated = DocumentRegister.labReport(UUID.randomUUID(), null, null, "r.pdf");
        assertEquals(List.of(later, lab, undated), DocumentRegister.newestFirst(List.of(undated, lab, later)));
    }
}
