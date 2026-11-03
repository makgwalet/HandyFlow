package za.co.handyflow.platform.secureaccess.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import za.co.handyflow.platform.secureaccess.application.SecureShareProvider.ReleasedFile;

import static org.junit.jupiter.api.Assertions.*;

/** The engine never trusts a module's file name or content type: both are made safe again on the way out. */
class SecureDownloadsTest {

    @Test
    void contentType_browserRunnableTypesBecomeOpaque() {
        assertEquals("application/octet-stream", SecureDownloads.contentType("text/html; charset=utf-8"));
        assertEquals("application/octet-stream", SecureDownloads.contentType("image/svg+xml"));
        assertEquals("application/octet-stream", SecureDownloads.contentType("application/xml"));
        assertEquals("application/octet-stream", SecureDownloads.contentType("text/javascript"));
        assertEquals("application/octet-stream", SecureDownloads.contentType("text/css"));
        assertEquals("application/octet-stream", SecureDownloads.contentType(null));
        assertEquals("application/octet-stream", SecureDownloads.contentType("not a type"));
    }

    @Test
    void contentType_ordinaryDocumentsPassThrough() {
        assertEquals("application/pdf", SecureDownloads.contentType("Application/PDF"));
        assertEquals("image/png", SecureDownloads.contentType("image/png"));
    }

    @Test
    void safeName_stripsPathsAndHeaderBreakers() {
        assertEquals("passwd", SecureDownloads.safeName("../../etc/passwd"));
        assertEquals("a_b.pdf", SecureDownloads.safeName("a\"b.pdf"));
        assertEquals("hidden", SecureDownloads.safeName("...hidden"));
        assertEquals("document", SecureDownloads.safeName("   "));
        assertEquals("document", SecureDownloads.safeName(null));
        assertEquals(150, SecureDownloads.safeName("x".repeat(400)).length());
    }

    @Test
    void response_isAlwaysAnAttachmentThatIsNeverSniffedOrCached() {
        ResponseEntity<byte[]> r = SecureDownloads.response(new ReleasedFile("plan.html", "text/html", new byte[]{1, 2, 3}));
        assertEquals("application/octet-stream", r.getHeaders().getContentType().toString());
        assertTrue(r.getHeaders().getFirst("Content-Disposition").startsWith("attachment"));
        assertEquals("nosniff", r.getHeaders().getFirst("X-Content-Type-Options"));
        assertTrue(r.getHeaders().getCacheControl().contains("no-store"));
        assertEquals(3, r.getHeaders().getContentLength());
    }
}
