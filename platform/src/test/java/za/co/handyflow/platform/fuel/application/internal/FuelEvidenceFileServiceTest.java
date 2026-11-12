package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryEvidence;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryEvidenceRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.EvidenceFileDtos.StoredFile;
import za.co.handyflow.platform.fuel.dto.EvidenceFileDtos.Upload;
import za.co.handyflow.platform.fuel.dto.FuelDistributionDtos.EvidenceView;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelEvidenceFileServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID ALICE = UUID.randomUUID(), DRIVER_USER = UUID.randomUUID(), DRIVER_EMP = UUID.randomUUID(), OTHER_EMP = UUID.randomUUID();
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};
    static final byte[] EXE = {'M', 'Z', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelDeliveryEvidenceRepository evidence;
    @Mock HrFacade hr;
    @Mock FileStorageService storage;
    FuelEvidenceFileService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new FuelEvidenceFileService(deliveries, evidence, hr, storage);
        when(evidence.save(any())).thenAnswer(i -> i.getArgument(0));
        when(storage.store(anyString(), anyString(), anyString(), any())).thenReturn("key-1");
    }

    private FuelDelivery delivery(String status, UUID driverEmp) {
        FuelDelivery d = mock(FuelDelivery.class);
        UUID id = UUID.randomUUID();
        when(d.getId()).thenReturn(id); when(d.getStatus()).thenReturn(status); when(d.getDriverEmployeeId()).thenReturn(driverEmp);
        when(deliveries.findActiveById(any(TenantId.class), eq(id))).thenReturn(Optional.of(d));
        return d;
    }

    private static Upload up(String kind, byte[] b) { return new Upload(kind, "IMG_0042.JPG", b, "at the gate", null); }

    @Test @DisplayName("a photo is stored, described by its real type, and shown by file name, not by the store's key")
    void photoStored() throws IOException {
        FuelDelivery d = delivery("IN_TRANSIT", DRIVER_EMP);
        EvidenceView v = service.addAsOffice(T, d.getId(), up("PHOTO", JPEG), ALICE);
        assertTrue(v.fileStored()); assertEquals("image/jpeg", v.contentType()); assertEquals(JPEG.length, v.fileSizeBytes());
        assertEquals("IMG_0042.jpg", v.fileRef());
        verify(storage).store(eq("fuel/evidence/" + T + "/" + d.getId()), eq("IMG_0042.jpg"), eq("image/jpeg"), eq(JPEG));
    }

    @Test @DisplayName("the type comes from the bytes: a program renamed .jpg is refused and nothing is stored")
    void disguisedFileRefused() throws IOException {
        FuelDelivery d = delivery("IN_TRANSIT", DRIVER_EMP);
        assertThrows(IllegalArgumentException.class, () -> service.addAsOffice(T, d.getId(), up("PHOTO", EXE), ALICE));
        verify(storage, never()).store(any(), any(), any(), any());
    }

    @Test @DisplayName("a PDF is accepted only as a document; empty and oversize files are refused")
    void typeAndSizeRules() {
        assertEquals("application/pdf", FuelEvidenceFileService.validate(up("DOCUMENT", PDF)));
        assertThrows(IllegalArgumentException.class, () -> FuelEvidenceFileService.validate(up("PHOTO", PDF)));
        assertEquals("image/png", FuelEvidenceFileService.validate(up("SIGNATURE", PNG)));
        assertThrows(IllegalArgumentException.class, () -> FuelEvidenceFileService.validate(up("PHOTO", new byte[0])));
        byte[] big = new byte[FuelEvidenceFileService.MAX_BYTES + 1]; System.arraycopy(JPEG, 0, big, 0, JPEG.length);
        assertThrows(IllegalArgumentException.class, () -> FuelEvidenceFileService.validate(up("PHOTO", big)));
        assertThrows(IllegalArgumentException.class, () -> FuelEvidenceFileService.validate(up("GPS", JPEG)));
    }

    @Test @DisplayName("file names are cleaned: no folders, no odd characters, and the extension matches the real type")
    void safeNames() {
        assertEquals("evidence.png", FuelEvidenceFileService.safeName(null, "image/png"));
        assertEquals("passwd.jpg", FuelEvidenceFileService.safeName("../../etc/passwd.exe", "image/jpeg"));
        assertEquals("evidence.pdf", FuelEvidenceFileService.safeName("<>.html", "application/pdf"));
        assertTrue(FuelEvidenceFileService.safeName("a".repeat(300) + ".jpg", "image/jpeg").length() <= 64);
    }

    @Test @DisplayName("evidence cannot be added before the load is on the road")
    void notBeforeDispatch() {
        FuelDelivery d = delivery("SCHEDULED", DRIVER_EMP);
        assertThrows(IllegalStateException.class, () -> service.addAsOffice(T, d.getId(), up("PHOTO", JPEG), ALICE));
        verify(evidence, never()).save(any());
    }

    @Test @DisplayName("a replay of an upload the server already saved returns the first record and stores no second file")
    void replayIsHarmless() throws IOException {
        FuelDelivery d = delivery("IN_TRANSIT", DRIVER_EMP);
        UUID req = UUID.randomUUID();
        FuelDeliveryEvidence first = FuelDeliveryEvidence.createFile(T, d.getId(), "PHOTO", "key-0", "image/jpeg", 10, "a.jpg", null, ALICE);
        when(evidence.findByClientRequest(any(TenantId.class), eq(d.getId()), eq(req))).thenReturn(Optional.of(first));
        EvidenceView v = service.addAsOffice(T, d.getId(), new Upload("PHOTO", "a.jpg", JPEG, null, req), ALICE);
        assertEquals(first.getId(), v.id());
        verify(storage, never()).store(any(), any(), any(), any());
        verify(evidence, never()).save(any());
    }

    @Test @DisplayName("if the record cannot be saved, the file just stored is removed again")
    void orphanRemoved() throws IOException {
        FuelDelivery d = delivery("IN_TRANSIT", DRIVER_EMP);
        when(evidence.save(any())).thenThrow(new IllegalStateException("db down"));
        assertThrows(IllegalStateException.class, () -> service.addAsOffice(T, d.getId(), up("PHOTO", JPEG), ALICE));
        verify(storage).delete("key-1");
    }

    @Test @DisplayName("a store failure is reported plainly and no record is made")
    void storeFailure() throws IOException {
        FuelDelivery d = delivery("IN_TRANSIT", DRIVER_EMP);
        when(storage.store(anyString(), anyString(), anyString(), any())).thenThrow(new IOException("disk"));
        assertThrows(IllegalStateException.class, () -> service.addAsOffice(T, d.getId(), up("PHOTO", JPEG), ALICE));
        verify(evidence, never()).save(any());
    }

    @Test @DisplayName("download returns the bytes with the stored type; a record with no file is not found")
    void download() throws IOException {
        FuelDeliveryEvidence e = FuelDeliveryEvidence.createFile(T, UUID.randomUUID(), "SIGNATURE", "key-9", "image/png", 10, "sig.png", null, ALICE);
        when(evidence.findOne(any(TenantId.class), eq(e.getId()))).thenReturn(Optional.of(e));
        when(storage.retrieve("key-9")).thenReturn(PNG);
        StoredFile f = service.download(T, e.getId());
        assertEquals("image/png", f.contentType()); assertArrayEquals(PNG, f.bytes()); assertEquals("sig.png", f.filename());

        FuelDeliveryEvidence typed = FuelDeliveryEvidence.create(T, UUID.randomUUID(), "PHOTO", "scans/a.jpg", null, null, null, null, ALICE);
        when(evidence.findOne(any(TenantId.class), eq(typed.getId()))).thenReturn(Optional.of(typed));
        assertThrows(ResourceNotFoundException.class, () -> service.download(T, typed.getId()));
        UUID missing = UUID.randomUUID();
        when(evidence.findOne(any(TenantId.class), eq(missing))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.download(T, missing));
    }

    @Test @DisplayName("a driver can attach to and download from their own load only")
    void driverOwnLoadOnly() throws IOException {
        EmployeeSummary me = new EmployeeSummary(DRIVER_EMP, "E1", "Dan", "Driver", "Dan Driver", null, null, "Driver", null, "ACTIVE");
        when(hr.findEmployeeByUserId(any(TenantId.class), eq(DRIVER_USER))).thenReturn(Optional.of(me));
        FuelDelivery mine = delivery("IN_TRANSIT", DRIVER_EMP);
        FuelDelivery theirs = delivery("IN_TRANSIT", OTHER_EMP);
        assertTrue(service.addAsDriver(T, DRIVER_USER, mine.getId(), up("PHOTO", JPEG)).fileStored());
        assertThrows(ResourceNotFoundException.class, () -> service.addAsDriver(T, DRIVER_USER, theirs.getId(), up("PHOTO", JPEG)));
        assertThrows(IllegalStateException.class, () -> service.addAsDriver(T, null, mine.getId(), up("PHOTO", JPEG)));

        FuelDeliveryEvidence onTheirs = FuelDeliveryEvidence.createFile(T, theirs.getId(), "PHOTO", "k", "image/jpeg", 10, "a.jpg", null, ALICE);
        when(evidence.findOne(any(TenantId.class), eq(onTheirs.getId()))).thenReturn(Optional.of(onTheirs));
        assertThrows(ResourceNotFoundException.class, () -> service.downloadAsDriver(T, DRIVER_USER, onTheirs.getId()));
    }
}
