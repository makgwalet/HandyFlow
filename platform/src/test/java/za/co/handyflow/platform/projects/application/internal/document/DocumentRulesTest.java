package za.co.handyflow.platform.projects.application.internal.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentRulesTest {

    @Test
    void prefixesMatchTheSqlBackfill() {
        assertEquals("DWG", DocumentRules.prefixFor("DRAWING"));
        assertEquals("RFI", DocumentRules.prefixFor("RFI"));
        assertEquals("SUB", DocumentRules.prefixFor("SUBMITTAL"));
        assertEquals("CTR", DocumentRules.prefixFor("CONTRACT"));
        assertEquals("RPT", DocumentRules.prefixFor("REPORT"));
        assertEquals("PHO", DocumentRules.prefixFor("PHOTO"));
        assertEquals("GEN", DocumentRules.prefixFor("GENERAL"));
        assertEquals("GEN", DocumentRules.prefixFor(null));
        assertEquals("DWG", DocumentRules.prefixFor("drawing"));
    }

    @Test
    void autoNumberIsPrefixPlusPaddedSequence() {
        assertEquals("DWG-001", DocumentRules.autoNumber("DRAWING", 1));
        assertEquals("SUB-042", DocumentRules.autoNumber("SUBMITTAL", 42));
        assertEquals("GEN-1000", DocumentRules.autoNumber("GENERAL", 1000));
    }

    @Test
    void normalizeNumber_trimsAndCollapses_andBlankMeansNone() {
        assertEquals("DWG-A-101", DocumentRules.normalizeNumber("  DWG-A-101 "));
        assertEquals("A 1", DocumentRules.normalizeNumber("A    1"));
        assertNull(DocumentRules.normalizeNumber("   "));
        assertNull(DocumentRules.normalizeNumber(null));
    }

    @Test
    void normalizeNumber_rejectsOverlongValues() {
        try {
            DocumentRules.normalizeNumber("X".repeat(51));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { /* ok */ }
        assertEquals(50, DocumentRules.normalizeNumber("X".repeat(50)).length());
    }

    @Test
    void executablesAreBlocked_byLastExtension() {
        assertTrue(DocumentRules.hasBlockedExtension("setup.exe"));
        assertTrue(DocumentRules.hasBlockedExtension("run.BAT"));
        assertTrue(DocumentRules.hasBlockedExtension("report.pdf.exe"));      // disguised
        assertTrue(DocumentRules.hasBlockedExtension("script.ps1"));
        assertTrue(DocumentRules.hasBlockedExtension("tool.jar"));
    }

    @Test
    void ordinaryDocumentsAreAllowed() {
        assertFalse(DocumentRules.hasBlockedExtension("foundation-rev-a.pdf"));
        assertFalse(DocumentRules.hasBlockedExtension("plan.dwg"));
        assertFalse(DocumentRules.hasBlockedExtension("photo.JPG"));
        assertFalse(DocumentRules.hasBlockedExtension("archive.exe.pdf"));    // only the LAST extension counts
        assertFalse(DocumentRules.hasBlockedExtension("README"));
        assertFalse(DocumentRules.hasBlockedExtension("trailingdot."));
        assertFalse(DocumentRules.hasBlockedExtension(null));
        assertFalse(DocumentRules.hasBlockedExtension("  "));
    }

    @Test
    void urls_areCheckedOnTheirLastPathSegment_ignoringQueryAndFragment() {
        assertTrue(DocumentRules.hasBlockedExtension("https://cdn.example.com/files/setup.exe"));
        assertTrue(DocumentRules.hasBlockedExtension("https://cdn.example.com/files/setup.exe?token=abc"));
        assertTrue(DocumentRules.hasBlockedExtension("https://cdn.example.com/files/setup.exe#frag"));
        assertFalse(DocumentRules.hasBlockedExtension("https://cdn.example.com/files/drawing.pdf?download=setup.exe"));
        assertFalse(DocumentRules.hasBlockedExtension("https://cdn.example.com/v1.2/drawing.pdf"));
        assertTrue(DocumentRules.hasBlockedExtension("C:\\temp\\evil.cmd"));
    }

    // ── Links ────────────────────────────────────────────────────────────────

    @Test
    void isSafeUrl_allowsHttpHttpsAndRelativeReferences() {
        assertTrue(DocumentRules.isSafeUrl("https://example.com/a.pdf"));
        assertTrue(DocumentRules.isSafeUrl("HTTP://example.com/a.pdf"));
        assertTrue(DocumentRules.isSafeUrl("projects/123/a.pdf"));
        assertTrue(DocumentRules.isSafeUrl("/api/v1/projects/documents/1/download"));
        assertTrue(DocumentRules.isSafeUrl("folder/a:b.pdf"));            // colon inside a path is not a scheme
    }

    @Test
    void isSafeUrl_refusesScriptingAndLocalSchemes_evenWhenDisguised() {
        assertFalse(DocumentRules.isSafeUrl("javascript:alert(1)"));
        assertFalse(DocumentRules.isSafeUrl("JavaScript:alert(1)"));
        assertFalse(DocumentRules.isSafeUrl("java\tscript:alert(1)"));     // browsers drop the tab
        assertFalse(DocumentRules.isSafeUrl("java\nscript:alert(1)"));
        assertFalse(DocumentRules.isSafeUrl(" \u0001javascript:alert(1)"));
        assertFalse(DocumentRules.isSafeUrl("data:text/html;base64,AAAA"));
        assertFalse(DocumentRules.isSafeUrl("vbscript:x"));
        assertFalse(DocumentRules.isSafeUrl("file:///etc/passwd"));
        assertFalse(DocumentRules.isSafeUrl("ftp://host/a.pdf"));
        assertFalse(DocumentRules.isSafeUrl("C:\\temp\\a.pdf"));
    }

    @Test
    void isSafeUrl_refusesNullAndBlank() {
        assertFalse(DocumentRules.isSafeUrl(null));
        assertFalse(DocumentRules.isSafeUrl(""));
        assertFalse(DocumentRules.isSafeUrl("  \t "));
    }

    // ── Stored files ─────────────────────────────────────────────────────────

    @Test
    void safeFileName_dropsDirectoriesAndDangerousCharacters() {
        assertEquals("a.pdf", DocumentRules.safeFileName("C:\\x\\y\\a.pdf"));
        assertEquals("passwd", DocumentRules.safeFileName("../../etc/passwd"));
        assertEquals("a_b_c.pdf", DocumentRules.safeFileName("a\"b;c.pdf"));
        assertEquals("a__b.pdf", DocumentRules.safeFileName("a\r\nb.pdf"));
        assertEquals("hidden.txt", DocumentRules.safeFileName("..hidden.txt"));
        assertEquals("document", DocumentRules.safeFileName(null));
        assertEquals("document", DocumentRules.safeFileName("   "));
        assertEquals("document", DocumentRules.safeFileName("..."));
    }

    @Test
    void safeFileName_capsLengthButKeepsTheExtension() {
        String r = DocumentRules.safeFileName("x".repeat(400) + ".pdf");
        assertEquals(150, r.length());
        assertTrue(r.endsWith(".pdf"));
    }

    @Test
    void looksExecutable_recognisesProgramHeaders() {
        assertTrue(DocumentRules.looksExecutable(new byte[]{'M', 'Z', 0, 0}));
        assertTrue(DocumentRules.looksExecutable(new byte[]{0x7F, 'E', 'L', 'F'}));
        assertTrue(DocumentRules.looksExecutable(new byte[]{'#', '!', '/', 'b'}));
        assertTrue(DocumentRules.looksExecutable(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}));
        assertTrue(DocumentRules.looksExecutable(new byte[]{(byte) 0xFE, (byte) 0xED, (byte) 0xFA, (byte) 0xCF}));
        assertTrue(DocumentRules.looksExecutable(new byte[]{(byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE}));
    }

    @Test
    void looksExecutable_allowsOrdinaryDocuments() {
        assertFalse(DocumentRules.looksExecutable("%PDF-1.7".getBytes()));
        assertFalse(DocumentRules.looksExecutable(new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        assertFalse(DocumentRules.looksExecutable(new byte[]{'P', 'K', 3, 4}));      // zip / docx / xlsx
        assertFalse(DocumentRules.looksExecutable(new byte[]{'M'}));
        assertFalse(DocumentRules.looksExecutable(new byte[0]));
        assertFalse(DocumentRules.looksExecutable(null));
    }

    @Test
    void downloadContentType_keepsSafeTypes_andNeutralisesActiveOnes() {
        assertEquals("application/pdf", DocumentRules.downloadContentType("application/pdf"));
        assertEquals("image/png", DocumentRules.downloadContentType(" IMAGE/PNG; charset=binary"));
        assertEquals("application/octet-stream", DocumentRules.downloadContentType(null));
        assertEquals("application/octet-stream", DocumentRules.downloadContentType(""));
        assertEquals("application/octet-stream", DocumentRules.downloadContentType("not a type"));
        assertEquals("application/octet-stream", DocumentRules.downloadContentType("text/html"));
        assertEquals("application/octet-stream", DocumentRules.downloadContentType("text/html; charset=utf-8"));
        assertEquals("application/octet-stream", DocumentRules.downloadContentType("image/svg+xml"));
        assertEquals("application/octet-stream", DocumentRules.downloadContentType("application/xhtml+xml"));
        assertEquals("application/octet-stream", DocumentRules.downloadContentType("text/javascript"));
        assertEquals("application/octet-stream", DocumentRules.downloadContentType("text/css"));
    }

    @Test
    void sha256_isLowerCaseHex() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                DocumentRules.sha256(new byte[0]));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                DocumentRules.sha256("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
