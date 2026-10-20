package za.co.handyflow.platform.documents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.documents.application.DocumentScanner;
import za.co.handyflow.platform.documents.application.DocumentsFacade;
import za.co.handyflow.platform.documents.application.DocumentsFacade.DocumentInfo;
import za.co.handyflow.platform.documents.application.DocumentsFacade.FileUpload;
import za.co.handyflow.platform.documents.domain.model.Document;
import za.co.handyflow.platform.documents.domain.model.DocumentVersion;
import za.co.handyflow.platform.documents.domain.repository.DocumentRepository;
import za.co.handyflow.platform.documents.domain.repository.DocumentVersionRepository;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DocumentsFacadeTest {
    DocumentRepository documents = mock(DocumentRepository.class);
    DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
    FileStorageService storage = mock(FileStorageService.class);
    DocumentScanner scanner = mock(DocumentScanner.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    DocumentsFacade facade;
    TenantId tenant = TenantId.generate();
    UUID actor = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    Map<UUID, Document> docs = new HashMap<>();
    List<DocumentVersion> stored = new ArrayList<>();

    static byte[] pdf(int extra) { byte[] b = new byte[40 + extra]; System.arraycopy("%PDF-1.7\n".getBytes(), 0, b, 0, 9); return b; }
    FileUpload file(int extra) { return new FileUpload("Policy.pdf", "application/pdf", pdf(extra)); }

    DocumentsFacade build(boolean scanRequired) throws Exception {
        Class<?> c = Class.forName("za.co.handyflow.platform.documents.application.internal.DocumentsFacadeImpl");
        Constructor<?> k = c.getDeclaredConstructors()[0];
        k.setAccessible(true);
        return (DocumentsFacade) k.newInstance(documents, versions, storage, scanner, trail, scanRequired);
    }

    @BeforeEach
    void setUp() throws Exception {
        facade = build(false);
        when(scanner.scan(any(), any(), any())).thenReturn(DocumentScanner.Result.skipped("none"));
        when(storage.store(any(), any(), any(), any())).thenAnswer(i -> i.getArgument(0) + "/key-" + UUID.randomUUID());
        when(documents.save(any())).thenAnswer(i -> { Document d = i.getArgument(0); docs.put(d.getId(), d); return d; });
        when(versions.save(any())).thenAnswer(i -> { stored.add(i.getArgument(0)); return i.getArgument(0); });
        when(documents.findOwned(eq(tenant), any())).thenAnswer(i -> Optional.ofNullable(docs.get(i.<UUID>getArgument(1))));
        when(versions.findOne(eq(tenant), any(), anyInt())).thenAnswer(i -> stored.stream()
                .filter(v -> v.getDocumentId().equals(i.getArgument(1)) && v.getVersionNo() == i.<Integer>getArgument(2)).findFirst());
    }

    DocumentInfo uploaded() {
        return facade.upload(tenant, "EARTH_ASSET", owner, "INSURANCE", "Policy", null, LocalDate.of(2027, 3, 1), file(0), actor);
    }

    @Test void aFileIsCheckedStoredPrivatelyAndRecordedWithItsHash() throws IOException {
        DocumentInfo d = uploaded();
        assertEquals(1, d.currentVersion());
        assertEquals("application/pdf", d.current().contentType());
        assertEquals("SKIPPED", d.current().scanStatus());
        assertEquals(64, d.current().sha256().length());
        verify(storage).store(startsWith("documents/" + tenant.getValue() + "/EARTH_ASSET/" + owner), eq("Policy.pdf"), eq("application/pdf"), any());
        verify(trail).record(eq(tenant), eq("DOCUMENT_UPLOADED"), eq("DOCUMENT"), any(UUID.class), any(), anyMap());
    }

    static String startsWith(String p) { return argThat(s -> s != null && s.startsWith(p)); }

    @Test void anInfectedFileIsRefusedAndNeverStored() throws IOException {
        when(scanner.scan(any(), any(), any())).thenReturn(new DocumentScanner.Result(DocumentScanner.Status.INFECTED, "Eicar"));
        assertThrows(IllegalArgumentException.class, this::uploaded);
        verify(storage, never()).store(any(), any(), any(), any());
        verify(documents, never()).save(any());
        verify(trail).record(eq(tenant), eq("DOCUMENT_REJECTED_INFECTED"), eq("DOCUMENT"), any(UUID.class), any(), anyMap());
    }

    @Test void whenScanningIsRequiredAnUnscannedFileIsRefused() throws Exception {
        facade = build(true);
        assertThrows(IllegalStateException.class, this::uploaded);
        when(scanner.scan(any(), any(), any())).thenReturn(DocumentScanner.Result.clean());
        assertEquals("CLEAN", uploaded().current().scanStatus());
    }

    @Test void aScannerThatFailsIsTreatedAsNotScanned() throws Exception {
        when(scanner.scan(any(), any(), any())).thenThrow(new RuntimeException("down"));
        assertEquals("SKIPPED", uploaded().current().scanStatus());
        facade = build(true);
        assertThrows(IllegalStateException.class, this::uploaded);
    }

    @Test void aDisguisedFileIsRefusedBeforeAnythingIsStored() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> facade.upload(tenant, "EARTH_ASSET", owner, "INSURANCE", "x", null, null,
                new FileUpload("a.pdf", "application/pdf", "MZ-an-exe-program-file".getBytes()), actor));
        verify(storage, never()).store(any(), any(), any(), any());
    }

    @Test void aNewVersionKeepsTheOldOneAndCanRenewTheExpiry() {
        DocumentInfo d = uploaded();
        DocumentInfo v2 = facade.addVersion(tenant, d.id(), file(5), "Renewed", LocalDate.of(2028, 3, 1), actor);
        assertEquals(2, v2.currentVersion());
        assertEquals(LocalDate.of(2028, 3, 1), v2.expiresOn());
        assertEquals(2, stored.size());
        assertEquals(1, stored.get(0).getVersionNo());
    }

    @Test void theSameFileAgainIsRefusedAndItsStoredCopyRemoved() throws IOException {
        DocumentInfo d = uploaded();
        assertThrows(IllegalArgumentException.class, () -> facade.addVersion(tenant, d.id(), file(0), null, null, actor));
        assertEquals(1, stored.size());
        verify(storage).delete(any());
    }

    @Test void anArchivedDocumentCannotBeChangedAndNeedsAReason() {
        DocumentInfo d = uploaded();
        assertThrows(IllegalArgumentException.class, () -> facade.archive(tenant, d.id(), "no", actor));
        DocumentInfo a = facade.archive(tenant, d.id(), "Policy cancelled", actor);
        assertEquals("ARCHIVED", a.status());
        assertThrows(IllegalStateException.class, () -> facade.addVersion(tenant, d.id(), file(7), null, null, actor));
        assertThrows(IllegalStateException.class, () -> facade.updateDetails(tenant, d.id(), "New", null, null, actor));
        assertEquals(1, stored.size());
    }

    @Test void anotherCompanyCannotReachADocument() {
        DocumentInfo d = uploaded();
        TenantId other = TenantId.generate();
        when(documents.findOwned(eq(other), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> facade.download(other, d.id(), null, actor));
        assertThrows(ResourceNotFoundException.class, () -> facade.addVersion(other, d.id(), file(3), null, null, actor));
        assertTrue(facade.find(other, d.id()).isEmpty());
    }

    @Test void aDownloadReturnsTheStoredBytesAndIsAudited() throws IOException {
        DocumentInfo d = uploaded();
        when(storage.retrieve(any())).thenReturn(pdf(0));
        DocumentsFacade.Download dl = facade.download(tenant, d.id(), null, actor);
        assertEquals("Policy.pdf", dl.fileName());
        verify(trail).record(eq(tenant), eq("DOCUMENT_DOWNLOADED"), eq("DOCUMENT"), eq(d.id()), any(), anyMap());
        assertThrows(ResourceNotFoundException.class, () -> facade.download(tenant, d.id(), 9, actor));
    }
}
