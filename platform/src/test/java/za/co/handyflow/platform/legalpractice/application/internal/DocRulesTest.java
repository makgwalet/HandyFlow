package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DocRulesTest {

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.ISO_8859_1); }

    @Test
    void aFileNameLosesItsFolderQuotesAndControlCharacters() {
        assertEquals("brief.pdf", DocRules.safeName("C:\\Users\\me\\brief.pdf"));
        assertEquals("brief.pdf", DocRules.safeName("../../etc/brief.pdf"));
        assertEquals("brief.pdf", DocRules.safeName("\"brief\".pdf".replace("\"", "")));
        assertEquals("a b.pdf", DocRules.safeName("a\u0000 b\n.pdf"));
        assertEquals("document", DocRules.safeName(null));
        assertEquals("document", DocRules.safeName("  "));
    }

    @Test
    void aVeryLongNameIsShortenedButKeepsItsExtension() {
        String n = DocRules.safeName("x".repeat(500) + ".pdf");
        assertEquals(200, n.length());
        assertTrue(n.endsWith(".pdf"));
    }

    @Test
    void theTypeComesFromTheExtensionNotFromWhatTheUploaderClaims() {
        assertEquals("application/pdf", DocRules.contentType("a.PDF"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", DocRules.contentType("a.docx"));
        assertEquals("application/octet-stream", DocRules.contentType("a.html"));
    }

    @Test
    void aGoodFileIsAccepted() {
        assertNull(DocRules.fileProblem("a.pdf", 100, b("%PDF-1.7")));
        assertNull(DocRules.fileProblem("a.docx", 100, b("PK\u0003\u0004")));
        assertNull(DocRules.fileProblem("a.txt", 5, b("hello")));
        assertNull(DocRules.fileProblem("a.png", 100, new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        assertNull(DocRules.fileProblem("a.jpg", 100, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertNull(DocRules.fileProblem("a.doc", 100, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0}));
    }

    @Test
    void anEmptyOrOversizeFileIsRefused() {
        assertEquals("The file is empty", DocRules.fileProblem("a.pdf", 0, new byte[0]));
        assertTrue(DocRules.fileProblem("a.pdf", DocRules.MAX_BYTES + 1, b("%PDF")).contains("over 20 MB"));
        assertNull(DocRules.fileProblem("a.pdf", DocRules.MAX_BYTES, b("%PDF")));
    }

    @Test
    void programsScriptsAndArchivesAreRefused() {
        for (String n : new String[]{"a.exe", "a.js", "a.html", "a.zip", "a.sh", "a.bat", "a.svg", "noextension"})
            assertTrue(DocRules.fileProblem(n, 10, b("MZ....")).contains("not accepted"), n);
    }

    @Test
    void aFileThatIsNotWhatItsNameSaysIsRefused() {
        assertTrue(DocRules.fileProblem("a.pdf", 10, b("MZ\u0090\u0000")).contains("does not look like a .pdf"));
        assertTrue(DocRules.fileProblem("a.docx", 10, b("%PDF")).contains("does not look like"));
        assertTrue(DocRules.fileProblem("a.png", 10, b("GIF89a")).contains("does not look like"));
        assertTrue(DocRules.fileProblem("a.txt", 10, new byte[]{'a', 0, 'b'}).contains("does not look like"));
        assertTrue(DocRules.fileProblem("a.pdf", 10, new byte[0]).contains("does not look like"));
    }

    @Test
    void titleCategoryAndReasonAreChecked() {
        assertNotNull(DocRules.titleProblem(null));
        assertNotNull(DocRules.titleProblem(" a "));
        assertNotNull(DocRules.titleProblem("x".repeat(256)));
        assertNull(DocRules.titleProblem("Letter of demand"));
        assertNotNull(DocRules.categoryProblem("MEMES"));
        assertNotNull(DocRules.categoryProblem(null));
        assertNull(DocRules.categoryProblem("PLEADING"));
        assertNotNull(DocRules.reasonProblem("short"));
        assertNotNull(DocRules.reasonProblem(null));
        assertNull(DocRules.reasonProblem("Litigation is likely"));
    }

    @Test
    void theHashIsSha256InLowercaseHex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", DocRules.sha256Hex(b("abc")));
        assertEquals(64, DocRules.sha256Hex(new byte[0]).length());
        assertNotEquals(DocRules.sha256Hex(b("abc")), DocRules.sha256Hex(b("abd")));
    }
}
