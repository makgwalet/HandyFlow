package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.*;

class ReportPackTest {

    private static final List<String> COLS = List.of("Ref", "Title", "Score");

    private static ReportFiles.Section section(String title, int rows) {
        List<List<String>> r = new ArrayList<>();
        for (int i = 0; i < rows; i++) r.add(List.of("R" + i, "Row " + i, "" + i));
        return new ReportFiles.Section(title, "sub", COLS, r);
    }

    /** The drawing commands of each page, in page order. */
    private static List<String> pageTexts(byte[] pdf) throws Exception {
        String raw = new String(pdf, StandardCharsets.ISO_8859_1);
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("/Length (\\d+) >>\\nstream\\n").matcher(raw);
        while (m.find()) {
            int start = m.end(), len = Integer.parseInt(m.group(1));
            Inflater inf = new Inflater();
            inf.setInput(pdf, start, len);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while (!inf.finished()) { int n = inf.inflate(buf); if (n == 0 && inf.needsInput()) break; bos.write(buf, 0, n); }
            out.add(bos.toString(StandardCharsets.ISO_8859_1));
        }
        return out;
    }

    private static int pageCount(byte[] pdf) { return Integer.parseInt(new String(pdf, StandardCharsets.ISO_8859_1).replaceAll("(?s).*/Count (\\d+).*", "$1")); }

    @Test @DisplayName("A pack has a cover page, then each report starting on a new page, with one running page count")
    void structure() throws Exception {
        byte[] pdf = ReportFiles.pdfPack("Board pack", "Prepared today", List.of(section("Alpha", 3), section("Beta", 3)));
        assertEquals(3, pageCount(pdf));
        List<String> pages = pageTexts(pdf);
        assertEquals(3, pages.size());
        assertTrue(pages.get(0).contains("(Board pack)") && pages.get(0).contains("(Contents)"));
        assertTrue(pages.get(1).contains("(Alpha)") && !pages.get(1).contains("(Beta)"));
        assertTrue(pages.get(2).contains("(Beta)"));
        for (int i = 0; i < 3; i++) assertTrue(pages.get(i).contains("(Page " + (i + 1) + " of 3)"), "page " + (i + 1));
    }

    @Test @DisplayName("The contents list gives each report's real first page, including after a long report")
    void contents() throws Exception {
        byte[] pdf = ReportFiles.pdfPack("P", null, List.of(section("Long", 200), section("Empty", 0), section("Last", 2)));
        List<String> pages = pageTexts(pdf);
        int longPages = 0;
        for (String p : pages) if (p.contains("(Long)") || p.contains("(Long \\(continued\\))")) longPages++;   // brackets inside a PDF string are escaped
        assertTrue(longPages > 1);
        String cover = pages.get(0);
        assertTrue(cover.contains("(1.  Long)") && cover.contains("(2)"));
        assertTrue(cover.contains("(" + (2 + longPages) + ")"), "Empty starts after the long report");
        assertTrue(cover.contains("(" + (3 + longPages) + ")"), "Last starts after Empty");
        // and the pages the contents point to really start those reports
        assertTrue(pages.get(1 + longPages).contains("(Empty)"));
        assertTrue(pages.get(2 + longPages).contains("(Last)"));
    }

    @Test @DisplayName("An empty pack is a single cover page that says nothing was chosen")
    void emptyPack() throws Exception {
        byte[] pdf = ReportFiles.pdfPack("P", null, List.of());
        assertEquals(1, pageCount(pdf));
        assertTrue(pageTexts(pdf).get(0).contains("No reports were selected"));
    }

    @Test @DisplayName("A missing cover title falls back to a default; more than 34 reports say how many are not listed")
    void coverEdges() throws Exception {
        assertTrue(pageTexts(ReportFiles.pdfPack(" ", null, List.of())).get(0).contains("(Report pack)"));
        List<ReportFiles.Section> many = new ArrayList<>();
        for (int i = 0; i < 36; i++) many.add(section("S" + i, 1));
        assertTrue(pageTexts(ReportFiles.pdfPack("P", null, many)).get(0).contains("2 more"));
    }

    @Test @DisplayName("The single-report PDF is unchanged: no cover, report title on page one")
    void singleUnchanged() throws Exception {
        byte[] pdf = ReportFiles.pdf("Solo", "sub", COLS, section("x", 3).rows());
        assertEquals(1, pageCount(pdf));
        String p = pageTexts(pdf).get(0);
        assertTrue(p.contains("(Solo)") && !p.contains("(Contents)") && p.contains("(Page 1 of 1)"));
    }

    @Test @DisplayName("Pack keys: upper-cased, de-duplicated in order, at least one, at most 20")
    void keys() {
        assertEquals(List.of("RISKS", "OBLIGATIONS"), ReportRules.packKeys(java.util.Arrays.asList("risks", " Risks ", "obligations", null, "")));
        assertThrows(IllegalArgumentException.class, () -> ReportRules.packKeys(List.of()));
        assertThrows(IllegalArgumentException.class, () -> ReportRules.packKeys(null));
        assertThrows(IllegalArgumentException.class, () -> ReportRules.packKeys(java.util.Arrays.asList(" ", null)));
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) many.add("K" + i);
        assertThrows(IllegalArgumentException.class, () -> ReportRules.packKeys(many));
        assertEquals(20, ReportRules.packKeys(many.subList(0, 20)).size());
    }

    @Test @DisplayName("Title, subtitle, row cut note and file name")
    void text() {
        assertEquals("Legal & Compliance board pack", ReportRules.packTitle(null));
        assertEquals("Legal & Compliance board pack", ReportRules.packTitle("  \n "));
        assertEquals("Q4 pack", ReportRules.packTitle(" Q4\n pack "));
        assertEquals(100, ReportRules.packTitle("x".repeat(300)).length());
        LocalDate d = LocalDate.of(2026, 11, 19);
        assertEquals("Prepared 2026-11-19 by Thandi · 3 report(s) · scorecard as of 2026-09-30", ReportRules.packSubtitle(d, "Thandi", 3, "2026-09-30"));
        assertEquals("Prepared 2026-11-19 · 1 report(s)", ReportRules.packSubtitle(d, null, 1, " "));
        assertFalse(ReportRules.packCuts(1000));
        assertTrue(ReportRules.packCuts(1001));
        assertEquals("", ReportRules.packNote(10));
        assertTrue(ReportRules.packNote(2500).contains("first 1000 of 2500"));
        assertEquals("legal-compliance-board-pack-2026-11-19.pdf", ReportRules.packFileName(d));
    }
}
