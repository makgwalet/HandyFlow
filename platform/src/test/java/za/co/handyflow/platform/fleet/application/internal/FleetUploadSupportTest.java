package za.co.handyflow.platform.fleet.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import za.co.handyflow.platform.shared.FileStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FleetUploadSupportTest {

    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    FileStorageService storage = mock(FileStorageService.class);
    FleetUploadSupport uploads = new FleetUploadSupport(storage);

    static byte[] bytes(int... b) {
        byte[] out = new byte[b.length];
        for (int i = 0; i < b.length; i++) out[i] = (byte) b[i];
        return out;
    }

    @Test void aRealWordDocumentIsAccepted() {
        MockMultipartFile f = new MockMultipartFile("file", "claim.docx", DOCX, bytes('P', 'K', 3, 4, 0, 0, 0, 0));
        assertEquals(DOCX, uploads.validate(f, Set.of(DOCX), 1024));
    }

    @Test void aWordDocumentThatIsNotAZipIsRefused() {
        MockMultipartFile f = new MockMultipartFile("file", "claim.docx", DOCX, bytes('h', 'e', 'l', 'l', 'o', '!'));
        assertThrows(IllegalArgumentException.class, () -> uploads.validate(f, Set.of(DOCX), 1024));
    }

    @Test void aPdfRenamedToJpegIsRefused() {
        MockMultipartFile f = new MockMultipartFile("file", "x.jpg", "image/jpeg", bytes('%', 'P', 'D', 'F', '-', '1'));
        assertThrows(IllegalArgumentException.class, () -> uploads.validate(f, FleetUploadSupport.IMAGE_TYPES, 1024));
    }

    @Test void anEmptyOrOversizedOrUnlistedFileIsRefused() {
        assertThrows(IllegalArgumentException.class, () ->
                uploads.validate(new MockMultipartFile("file", "a.png", "image/png", new byte[0]), FleetUploadSupport.IMAGE_TYPES, 1024));
        assertThrows(IllegalArgumentException.class, () ->
                uploads.validate(new MockMultipartFile("file", "a.png", "image/png", new byte[2048]), FleetUploadSupport.IMAGE_TYPES, 1024));
        assertThrows(IllegalArgumentException.class, () ->
                uploads.validate(new MockMultipartFile("file", "a.gif", "image/gif", bytes('G', 'I', 'F', '8', '9', 'a')), FleetUploadSupport.IMAGE_TYPES, 1024));
    }

    @Test void filesGoThroughTheSharedStorageAndTheOpaqueKeyIsReturned() throws IOException {
        MockMultipartFile f = new MockMultipartFile("file", "..\\evil/photo one.png", "image/png", bytes(0x89, 'P', 'N', 'G', 1, 2));
        when(storage.store(eq("fleet/t/v/inspections/i"), eq("photo one.png"), eq("image/png"), any(byte[].class))).thenReturn("opaque-key");
        assertEquals("opaque-key", uploads.store("fleet/t/v/inspections/i/", f, "image/png"));
        verify(storage).store(eq("fleet/t/v/inspections/i"), eq("photo one.png"), eq("image/png"), any(byte[].class));
    }

    @Test void aStorageFailureSurfacesAsAnIllegalState() throws IOException {
        MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png", bytes(0x89, 'P', 'N', 'G'));
        when(storage.store(any(), any(), any(), any())).thenThrow(new IOException("disk full"));
        assertThrows(IllegalStateException.class, () -> uploads.store("fleet/x", f, "image/png"));
    }

    @Test void openReturnsTheStoredBytes() throws IOException {
        when(storage.retrieve("k")).thenReturn(new byte[]{9, 8, 7});
        try (InputStream in = uploads.open("k")) {
            assertArrayEquals(new byte[]{9, 8, 7}, in.readAllBytes());
        }
    }

    @Test void aMissingStoredFileIsAnIllegalState() throws IOException {
        when(storage.retrieve("gone")).thenThrow(new IOException("no such key"));
        assertThrows(IllegalStateException.class, () -> uploads.open("gone"));
    }

    @Test void deleteQuietlyNeverThrows() throws IOException {
        doThrow(new IOException("boom")).when(storage).delete("k");
        assertDoesNotThrow(() -> uploads.deleteQuietly("k"));
    }

    @Test void displayNamesAreStrippedOfPathsAndOddCharacters() {
        assertEquals("passwd", uploads.sanitiseFileName("../../etc/passwd"));
        assertEquals("a_b.png", uploads.sanitiseFileName("a<b.png"));
        assertEquals("file", uploads.sanitiseFileName("  "));
        assertEquals("file", uploads.sanitiseFileName(null));
    }
}
