package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ReportFilesTest {

    private static final List<String> COLS = List.of("Reference", "Title", "Score");

    private static Map<String, String> unzip(byte[] b) throws Exception {
        Map<String, String> m = new HashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(b), StandardCharsets.UTF_8)) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) m.put(e.getName(), new String(z.readAllBytes(), StandardCharsets.UTF_8));
        }
        return m;
    }

    @Test @DisplayName("XLSX has every required part")
    void xlsxParts() throws Exception {
        Map<String, String> p = unzip(ReportFiles.xlsx("Register", "sub", COLS, List.of(List.of("A", "B", "1"))));
        for (String n : new String[]{"[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml"}) assertTrue(p.containsKey(n), n);
    }

    @Test @DisplayName("A cell starting with = is stored as text and never as a formula")
    void noFormulas() throws Exception {
        String sheet = unzip(ReportFiles.xlsx("R", "s", COLS, List.of(List.of("=1+1", "@SUM(A1)", "+cmd|calc")))).get("xl/worksheets/sheet1.xml");
        assertFalse(sheet.contains("<f>"));
        assertTrue(sheet.contains("t=\"inlineStr\""));
        assertTrue(sheet.contains(">=1+1<"));
    }

    @Test @DisplayName("Plain numbers become numeric cells, but identifiers with leading zeros stay text")
    void numbers() throws Exception {
        String sheet = unzip(ReportFiles.xlsx("R", "s", COLS, List.of(List.of("007", "85.5", "12")))).get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("<c r=\"A5\" t=\"inlineStr\"><is><t xml:space=\"preserve\">007</t>"));
        assertTrue(sheet.contains("<c r=\"B5\"><v>85.5</v></c>"));
        assertTrue(sheet.contains("<c r=\"C5\"><v>12</v></c>"));
    }

    @Test @DisplayName("XML special characters are escaped and illegal control characters are dropped")
    void escaping() throws Exception {
        String sheet = unzip(ReportFiles.xlsx("R", "s", COLS, List.of(List.of("a & <b> \"q\"", "bad\u0001char", "x")))).get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("a &amp; &lt;b&gt; &quot;q&quot;"));
        assertTrue(sheet.contains("badchar"));
        assertFalse(sheet.contains("\u0001"));
    }

    @Test @DisplayName("Sheet names are at most 31 characters and have no forbidden characters")
    void sheetNames() {
        assertEquals("Report", ReportFiles.sheetName(""));
        assertEquals("Report", ReportFiles.sheetName(null));
        String n = ReportFiles.sheetName("Obligations / Register: [2026] * ? very long title that goes on");
        assertTrue(n.length() <= 31);
        assertFalse(n.matches(".*[\\[\\]:*?/\\\\].*"));
    }

    @Test @DisplayName("Column letters carry past Z")
    void columnNames() {
        assertEquals("A", ReportFiles.colName(0));
        assertEquals("Z", ReportFiles.colName(25));
        assertEquals("AA", ReportFiles.colName(26));
        assertEquals("AB", ReportFiles.colName(27));
        assertEquals("BA", ReportFiles.colName(52));
    }

    @Test @DisplayName("An empty report still produces a valid workbook and PDF")
    void empty() throws Exception {
        assertNotNull(unzip(ReportFiles.xlsx("R", null, COLS, List.of())).get("xl/worksheets/sheet1.xml"));
        String pdf = new String(ReportFiles.pdf("R", null, COLS, List.of()), StandardCharsets.ISO_8859_1);
        assertTrue(pdf.startsWith("%PDF-1.4"));
        assertTrue(pdf.contains("/Count 1"));
    }

    @Test @DisplayName("PDF structure: header, trailer, and an xref whose offsets point at objects")
    void pdfStructure() {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 300; i++) rows.add(List.of("LC-" + i, "Row " + i + " " + "word ".repeat(10), "" + i));
        String pdf = new String(ReportFiles.pdf("Register", "sub", COLS, rows), StandardCharsets.ISO_8859_1);
        assertTrue(pdf.startsWith("%PDF-1.4"));
        assertTrue(pdf.trim().endsWith("%%EOF"));
        int pages = Integer.parseInt(pdf.replaceAll("(?s).*/Count (\\d+).*", "$1"));
        assertTrue(pages > 1, "300 rows should run to more than one page");
        int sx = Integer.parseInt(pdf.replaceAll("(?s).*startxref\\s+(\\d+)\\s+%%EOF.*", "$1"));
        assertTrue(pdf.substring(sx).startsWith("xref"));
        String[] lines = pdf.substring(sx).split("\n");
        int count = Integer.parseInt(lines[1].split(" ")[1]);
        for (int i = 1; i < count; i++) {
            int off = Integer.parseInt(lines[2 + i].substring(0, 10));
            assertTrue(pdf.startsWith(i + " 0 obj", off), "object " + i);
        }
    }

    @Test @DisplayName("Wrapping respects the width and the line cap, and splits a word wider than the column")
    void wrapping() {
        List<String> lines = ReportFiles.wrap("one two three four five six seven eight nine ten", 60, 8, false, 8);
        assertTrue(lines.size() > 1);
        for (String l : lines) assertTrue(ReportFiles.width(l, 8, false) <= 60.01, l);
        List<String> capped = ReportFiles.wrap("word ".repeat(200), 60, 8, false, 3);
        assertEquals(3, capped.size());
        assertTrue(capped.get(2).endsWith("..."));
        List<String> longWord = ReportFiles.wrap("A".repeat(100), 50, 8, false, 8);
        for (String l : longWord) assertTrue(ReportFiles.width(l, 8, false) <= 50.01);
        assertEquals(0, ReportFiles.wrap("   ", 50, 8, false, 3).size());
    }

    @Test @DisplayName("Characters outside Windows-1252 do not break text measurement")
    void unmappable() {
        assertTrue(ReportFiles.width("日本語 ok", 8, false) > 0);
        assertTrue(ReportFiles.width("Ünïcödé", 8, false) > 0);
    }

    @Test @DisplayName("File names carry the extension and the subtitle states the cut")
    void names() {
        assertEquals("legal-compliance-risk-register-2026-11-19.xlsx", ReportRules.fileName("RISK_REGISTER", LocalDate.of(2026, 11, 19), "xlsx"));
        assertEquals("legal-compliance-risk-register-2026-11-19.csv", ReportRules.fileName("RISK_REGISTER", LocalDate.of(2026, 11, 19)));
        String s = ReportRules.subtitle(Instant.parse("2026-11-19T10:00:00Z"), "Thandi", "2026-10-31", 5000, true);
        assertTrue(s.contains("2026-11-19 12:00"));
        assertTrue(s.contains("by Thandi"));
        assertTrue(s.contains("as of 2026-10-31"));
        assertTrue(s.contains("CUT at 5000"));
        assertFalse(ReportRules.subtitle(Instant.now(), null, null, 3, false).contains("CUT"));
    }

    @Test @DisplayName("Format names parse case-insensitively; blank means CSV; unknown is rejected")
    void formats() {
        assertEquals(ReportService.Format.CSV, ReportService.Format.parse(null));
        assertEquals(ReportService.Format.CSV, ReportService.Format.parse(" "));
        assertEquals(ReportService.Format.XLSX, ReportService.Format.parse("XLSX"));
        assertEquals(ReportService.Format.PDF, ReportService.Format.parse("pdf"));
        assertThrows(RuntimeException.class, () -> ReportService.Format.parse("docx"));
    }
}
