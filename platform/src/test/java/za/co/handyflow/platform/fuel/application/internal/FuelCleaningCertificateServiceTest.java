package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelCompartmentCleaning;
import za.co.handyflow.platform.fuel.domain.model.FuelTankerCompartment;
import za.co.handyflow.platform.fuel.domain.repository.FuelCompartmentCleaningRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankerCompartmentRepository;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.CleaningView;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Cleaning certificates: an uploaded file per cleaning, verified by a second person. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelCleaningCertificateServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID ALICE = UUID.randomUUID(), BOB = UUID.randomUUID(), CAROL = UUID.randomUUID();
    static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7', 10};
    static final byte[] EXE = {'M', 'Z', 0, 0, 0};

    @Mock FuelCompartmentCleaningRepository cleanings;
    @Mock FuelTankerCompartmentRepository tankerCompartments;
    @Mock FileStorageService storage;
    @InjectMocks FuelCleaningCertificateService service;

    UUID tankerId = UUID.randomUUID();
    FuelTankerCompartment comp;
    FuelCompartmentCleaning cleaning;

    @BeforeEach void setUp() throws IOException {
        comp = mock(FuelTankerCompartment.class);
        when(comp.getId()).thenReturn(UUID.randomUUID());
        when(tankerCompartments.findByTanker(T, tankerId)).thenReturn(List.of(comp));
        Instant now = Instant.now();
        cleaning = FuelCompartmentCleaning.record(T, comp.getId(), now.minusSeconds(3600), "Steam", "CERT-77", null, ALICE, now);
        when(cleanings.findOne(T, cleaning.getId())).thenReturn(Optional.of(cleaning));
        when(cleanings.save(any())).thenAnswer(i -> i.getArgument(0));
        when(storage.store(anyString(), anyString(), anyString(), any())).thenReturn("key-1");
    }

    @Test @DisplayName("a PDF certificate is stored against the cleaning and shown by its real type, without the store key")
    void attachesPdf() throws IOException {
        CleaningView v = service.attach(T, tankerId, comp.getId(), cleaning.getId(), "C:\\scans\\Cert 77!.pdf", PDF, BOB);
        assertTrue(v.fileStored()); assertEquals("application/pdf", v.contentType()); assertEquals("Cert 77.pdf", v.fileName());
        assertNull(v.verifiedAt());
        verify(storage).store(eq("fuel/cleaning/" + T + "/" + cleaning.getId()), eq("Cert 77.pdf"), eq("application/pdf"), any());
    }

    @Test @DisplayName("a file that is not an image or PDF is refused and nothing is stored")
    void refusesOtherTypes() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> service.attach(T, tankerId, comp.getId(), cleaning.getId(), "cert.pdf", EXE, BOB));
        assertThrows(IllegalArgumentException.class, () -> service.attach(T, tankerId, comp.getId(), cleaning.getId(), "cert.pdf", new byte[0], BOB));
        verify(storage, never()).store(anyString(), anyString(), anyString(), any());
    }

    @Test @DisplayName("one certificate per cleaning: a second upload is refused and the file is not stored")
    void secondUploadRefused() throws IOException {
        service.attach(T, tankerId, comp.getId(), cleaning.getId(), "a.pdf", PDF, BOB);
        clearInvocations(storage);
        assertThrows(IllegalStateException.class, () -> service.attach(T, tankerId, comp.getId(), cleaning.getId(), "b.pdf", PDF, BOB));
        verify(storage, never()).store(anyString(), anyString(), anyString(), any());
    }

    @Test @DisplayName("a failure after storing removes the orphaned file")
    void orphanRemoved() throws IOException {
        when(cleanings.save(any())).thenThrow(new RuntimeException("db down"));
        assertThrows(RuntimeException.class, () -> service.attach(T, tankerId, comp.getId(), cleaning.getId(), "a.pdf", PDF, BOB));
        verify(storage).delete("key-1");
    }

    @Test @DisplayName("a compartment of another tanker, or a cleaning of another compartment or tenant, is a 404")
    void ownership() {
        assertThrows(ResourceNotFoundException.class, () -> service.attach(T, tankerId, UUID.randomUUID(), cleaning.getId(), "a.pdf", PDF, BOB));
        assertThrows(ResourceNotFoundException.class, () -> service.attach(T, tankerId, comp.getId(), UUID.randomUUID(), "a.pdf", PDF, BOB));
        FuelTankerCompartment other = mock(FuelTankerCompartment.class);
        when(other.getId()).thenReturn(UUID.randomUUID());
        when(tankerCompartments.findByTanker(T, tankerId)).thenReturn(List.of(comp, other));
        assertThrows(ResourceNotFoundException.class, () -> service.attach(T, tankerId, other.getId(), cleaning.getId(), "a.pdf", PDF, BOB));
    }

    @Test @DisplayName("verifying needs an uploaded file and someone other than whoever recorded the cleaning or uploaded the file")
    void verifyRules() {
        assertThrows(IllegalStateException.class, () -> service.verify(T, tankerId, comp.getId(), cleaning.getId(), CAROL));     // no file yet
        service.attach(T, tankerId, comp.getId(), cleaning.getId(), "a.pdf", PDF, BOB);
        assertThrows(IllegalStateException.class, () -> service.verify(T, tankerId, comp.getId(), cleaning.getId(), ALICE));     // recorded it
        assertThrows(IllegalStateException.class, () -> service.verify(T, tankerId, comp.getId(), cleaning.getId(), BOB));       // uploaded it
        CleaningView v = service.verify(T, tankerId, comp.getId(), cleaning.getId(), CAROL);
        assertNotNull(v.verifiedAt()); assertEquals(CAROL, v.verifiedBy());
        assertTrue(cleaning.isVerifiedEvidence());
        assertThrows(IllegalStateException.class, () -> service.verify(T, tankerId, comp.getId(), cleaning.getId(), CAROL));     // already verified
    }

    @Test @DisplayName("a cleaning with a reference only is not verified evidence")
    void referenceAloneIsNotVerifiedEvidence() { assertFalse(cleaning.isVerifiedEvidence()); }

    @Test @DisplayName("downloading a cleaning with no file is a 404")
    void noFileNoDownload() {
        assertThrows(ResourceNotFoundException.class, () -> service.download(T, tankerId, comp.getId(), cleaning.getId()));
    }
}
