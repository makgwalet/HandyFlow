package za.co.handyflow.platform.documents;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.documents.domain.model.DocumentRules;

import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DocumentRulesTest {
    static byte[] pdf() { byte[] b = new byte[40]; System.arraycopy("%PDF-1.7\n".getBytes(), 0, b, 0, 9); return b; }
    static byte[] jpeg() { byte[] b = new byte[40]; b[0] = (byte) 0xFF; b[1] = (byte) 0xD8; b[2] = (byte) 0xFF; return b; }
    static byte[] png() { byte[] b = new byte[40]; b[0] = (byte) 0x89; b[1] = 'P'; b[2] = 'N'; b[3] = 'G'; return b; }
    static byte[] zip() { byte[] b = new byte[40]; b[0] = 'P'; b[1] = 'K'; b[2] = 3; b[3] = 4; return b; }

    @Test void theTypeComesFromTheFirstBytesNotTheUpload() {
        assertEquals(DocumentRules.PDF, DocumentRules.sniff(pdf(), "x.pdf"));
        assertEquals(DocumentRules.JPEG, DocumentRules.sniff(jpeg(), "x.jpg"));
        assertEquals(DocumentRules.PNG, DocumentRules.sniff(png(), "x.png"));
        assertEquals(DocumentRules.DOCX, DocumentRules.sniff(zip(), "Policy.DOCX"));
        assertEquals(DocumentRules.XLSX, DocumentRules.sniff(zip(), "sheet.xlsx"));
        assertNull(DocumentRules.sniff(zip(), "archive.zip"));
        assertNull(DocumentRules.sniff("MZ-an-exe-program-file".getBytes(), "invoice.pdf"));
        assertNull(DocumentRules.sniff(new byte[5], "a.pdf"));
    }

    @Test void aGoodFileIsAcceptedWithItsRealTypeAndHash() {
        DocumentRules.Checked c = DocumentRules.check("Policy.pdf", "application/pdf", pdf(), DocumentRules.GENERAL);
        assertEquals("Policy.pdf", c.fileName());
        assertEquals(DocumentRules.PDF, c.contentType());
        assertEquals(64, c.sha256().length());
        assertNotEquals(c.sha256(), DocumentRules.check("p.pdf", null, Arrays.copyOf(pdf(), 41), DocumentRules.GENERAL).sha256());
        assertDoesNotThrow(() -> DocumentRules.check("a.jpg", "image/jpg", jpeg(), DocumentRules.GENERAL));
        assertDoesNotThrow(() -> DocumentRules.check("a.pdf", "application/octet-stream", pdf(), DocumentRules.GENERAL));
    }

    @Test void emptyOversizeUnknownAndDisguisedFilesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.check("a.pdf", "application/pdf", new byte[0], DocumentRules.GENERAL));
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.check("a.pdf", "application/pdf", null, DocumentRules.GENERAL));
        byte[] big = new byte[(int) DocumentRules.MAX_BYTES + 1];
        System.arraycopy(pdf(), 0, big, 0, 20);
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.check("big.pdf", "application/pdf", big, DocumentRules.GENERAL));
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.check("run.exe", "application/pdf", "MZ-an-exe-program-file".getBytes(), DocumentRules.GENERAL));
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.check("a.pdf", "image/png", pdf(), DocumentRules.GENERAL));
    }

    @Test void photosMustBeImages() {
        assertEquals(DocumentRules.IMAGES, DocumentRules.allowedFor("PHOTO"));
        assertEquals(DocumentRules.GENERAL, DocumentRules.allowedFor("MANUAL"));
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.check("a.pdf", "application/pdf", pdf(), DocumentRules.allowedFor("PHOTO")));
        assertDoesNotThrow(() -> DocumentRules.check("a.png", "image/png", png(), DocumentRules.allowedFor("PHOTO")));
    }

    @Test void filenamesAreCleaned() {
        assertEquals("evil.pdf", DocumentRules.cleanName("..\\..\\temp/evil.pdf"));
        assertEquals("a b.pdf", DocumentRules.cleanName("  a\u0000 b.pdf "));
        assertEquals(150, DocumentRules.cleanName("x".repeat(400) + ".pdf").length());
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.cleanName("   "));
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.cleanName(".."));
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.cleanName(null));
    }

    @Test void titlesDescriptionsAndExpiryAreChecked() {
        assertEquals("Policy", DocumentRules.title(" Policy "));
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.title(" "));
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.title("x".repeat(151)));
        assertNull(DocumentRules.optional("  ", 10, "note"));
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.optional("x".repeat(11), 10, "note"));
        LocalDate today = LocalDate.of(2026, 10, 20);
        assertNull(DocumentRules.expiry(null, today));
        assertEquals(today.plusYears(1), DocumentRules.expiry(today.plusYears(1), today));
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.expiry(today.plusYears(31), today));
    }
}
