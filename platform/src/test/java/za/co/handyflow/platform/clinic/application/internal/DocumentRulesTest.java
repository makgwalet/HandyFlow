package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DocumentRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    static final byte[] PDF = "%PDF-1.4 test".getBytes();
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};

    private static DocumentRules.Clean ok(String type, String title, LocalDate date, String file, String ct, byte[] bytes) {
        return DocumentRules.upload(type, title, date, null, file, ct, bytes, TODAY);
    }

    @Test
    void aGoodUploadIsCleanedAndTheTypeComesFromTheBytes() {
        var c = DocumentRules.upload(" outside report ", "  Dr Smith letter ", TODAY, "  see page 2 ", "C:\\scans\\letter 1.PDF", "application/pdf", PDF, TODAY);
        assertEquals("OUTSIDE_REPORT", c.type());
        assertEquals("Dr Smith letter", c.title());
        assertEquals("see page 2", c.notes());
        assertEquals("letter 1.pdf", c.fileName());
        assertEquals("application/pdf", c.contentType());
    }

    @Test
    void aRenamedFileIsRefusedWhateverItIsCalledOrSaysItIs() {
        byte[] exe = {'M', 'Z', 0, 0, 0, 0};
        assertThrows(IllegalArgumentException.class, () -> ok("LETTER", "x", TODAY, "virus.pdf", "application/pdf", exe));
        var e = assertThrows(IllegalArgumentException.class, () -> ok("LETTER", "x", TODAY, "pic.png", "image/png", PDF));
        assertTrue(e.getMessage().contains("not the type it says"));
        assertEquals("image/png", ok("IMAGING", "Xray", TODAY, "x.bin", "application/octet-stream", PNG).contentType());
        assertEquals("image/jpeg", ok("IMAGING", "Photo", TODAY, "p.jpg", "image/jpg", JPG).contentType());
    }

    @Test
    void theNameLosesFoldersOddCharactersAndTakesTheRealExtension() {
        assertEquals("scan.png", DocumentRules.safeName("../../etc/scan.pdf", "image/png"));
        assertEquals("document.pdf", DocumentRules.safeName("$$$.exe", "application/pdf"));
        assertEquals("document.jpg", DocumentRules.safeName(null, "image/jpeg"));
    }

    @Test
    void typeTitleDateAndSizeAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> ok("SICK_NOTE", "x", TODAY, "a.pdf", null, PDF));   // issued types are system-only
        assertThrows(IllegalArgumentException.class, () -> ok(null, "x", TODAY, "a.pdf", null, PDF));
        assertThrows(IllegalArgumentException.class, () -> ok("LETTER", "  ", TODAY, "a.pdf", null, PDF));
        assertThrows(IllegalArgumentException.class, () -> ok("LETTER", "x", null, "a.pdf", null, PDF));
        assertThrows(IllegalArgumentException.class, () -> ok("LETTER", "x", TODAY.plusDays(1), "a.pdf", null, PDF));
        assertThrows(IllegalArgumentException.class, () -> ok("LETTER", "x", TODAY, "a.pdf", null, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> ok("LETTER", "x".repeat(201), TODAY, "a.pdf", null, PDF));
        byte[] big = new byte[(int) DocumentRules.MAX_BYTES + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertThrows(IllegalArgumentException.class, () -> ok("LETTER", "x", TODAY, "a.pdf", null, big));
    }

    @Test
    void removingNeedsAReason() {
        assertEquals("Wrong patient", DocumentRules.reason(" Wrong patient "));
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.reason(" "));
        assertThrows(IllegalArgumentException.class, () -> DocumentRules.reason("r".repeat(301)));
    }
}
