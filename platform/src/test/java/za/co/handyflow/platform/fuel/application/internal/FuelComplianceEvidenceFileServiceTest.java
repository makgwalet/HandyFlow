package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceEvidence;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceItem;
import za.co.handyflow.platform.fuel.domain.repository.FuelComplianceEvidenceRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelComplianceItemRepository;
import za.co.handyflow.platform.fuel.dto.ComplianceLinkageDtos.EvidenceView;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** F01 leftovers: licence evidence files and second-person verification. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelComplianceEvidenceFileServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID ALICE = UUID.randomUUID(), BOB = UUID.randomUUID();
    static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7', 10};
    static final byte[] EXE = {'M', 'Z', 0, 0, 0};

    @Mock FuelComplianceItemRepository items;
    @Mock FuelComplianceEvidenceRepository evidence;
    @Mock FileStorageService storage;
    @InjectMocks FuelComplianceEvidenceFileService files;

    UUID itemId = UUID.randomUUID();

    private void itemExists() {
        FuelComplianceItem i = FuelComplianceItem.create(T, "LICENCE", "Wholesale licence", null, "COMPANY", null, "Company", null, LocalDate.now().plusDays(90), null);
        when(items.findActiveById(T, itemId)).thenReturn(Optional.of(i));
        when(evidence.save(any())).thenAnswer(a -> a.getArgument(0));
    }

    @Test @DisplayName("a PDF scan is stored, described by its real type, and its store key is not shown in the view")
    void storesPdf() throws IOException {
        itemExists();
        when(storage.store(anyString(), anyString(), anyString(), any())).thenReturn("key-123");
        EvidenceView v = files.add(T, itemId, "C:\\scans\\Licence 2026!.pdf", PDF, "certified copy", ALICE);
        assertTrue(v.fileStored()); assertEquals("application/pdf", v.contentType()); assertEquals("Licence 2026.pdf", v.fileName());
        assertNull(v.fileRef()); assertNull(v.verifiedAt());
        verify(storage).store(eq("fuel/compliance/" + T + "/" + itemId), eq("Licence 2026.pdf"), eq("application/pdf"), any());
    }

    @Test @DisplayName("a file that is not an image or PDF is refused, whatever it is called, and nothing is stored")
    void refusesOtherTypes() throws IOException {
        itemExists();
        assertThrows(IllegalArgumentException.class, () -> files.add(T, itemId, "licence.pdf", EXE, null, ALICE));
        assertThrows(IllegalArgumentException.class, () -> files.add(T, itemId, "licence.pdf", new byte[0], null, ALICE));
        verify(storage, never()).store(anyString(), anyString(), anyString(), any());
    }

    @Test @DisplayName("an unknown or foreign item is a 404 before anything is stored")
    void unknownItem() throws IOException {
        when(items.findActiveById(T, itemId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> files.add(T, itemId, "a.pdf", PDF, null, ALICE));
        verify(storage, never()).store(anyString(), anyString(), anyString(), any());
    }

    @Test @DisplayName("if saving the record fails, the stored file is removed so no orphan is left")
    void removesOrphan() throws IOException {
        itemExists();
        when(storage.store(anyString(), anyString(), anyString(), any())).thenReturn("key-9");
        when(evidence.save(any())).thenThrow(new IllegalStateException("db down"));
        assertThrows(IllegalStateException.class, () -> files.add(T, itemId, "a.pdf", PDF, null, ALICE));
        verify(storage).delete("key-9");
    }

    @Test @DisplayName("a typed reference cannot be downloaded as a file")
    void referenceHasNoFile() {
        FuelComplianceEvidence ref = FuelComplianceEvidence.create(T, itemId, "https://drive/x", null, ALICE);
        when(evidence.findOne(T, ref.getId())).thenReturn(Optional.of(ref));
        assertThrows(ResourceNotFoundException.class, () -> files.download(T, ref.getId()));
    }

    @Test @DisplayName("verification: a different person can verify once; the person who added it cannot; a replaced one cannot")
    void verification() {
        FuelComplianceEvidence e = FuelComplianceEvidence.create(T, itemId, "ref", null, ALICE);
        assertThrows(IllegalStateException.class, () -> e.verify(ALICE));
        assertThrows(IllegalStateException.class, () -> e.verify(null));
        e.verify(BOB);
        assertTrue(e.isVerified()); assertEquals(BOB, e.getVerifiedBy());
        assertThrows(IllegalStateException.class, () -> e.verify(BOB));
        FuelComplianceEvidence old = FuelComplianceEvidence.create(T, itemId, "ref2", null, ALICE);
        old.supersede("wrong scan", ALICE);
        assertThrows(IllegalStateException.class, () -> old.verify(BOB));
    }
}
