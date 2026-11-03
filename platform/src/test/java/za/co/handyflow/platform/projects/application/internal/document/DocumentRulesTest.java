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
}