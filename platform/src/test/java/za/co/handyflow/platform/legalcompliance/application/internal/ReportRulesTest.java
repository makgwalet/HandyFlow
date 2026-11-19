package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportRulesTest {

    @Test @DisplayName("Cells that a spreadsheet would run as a formula get a leading apostrophe; plain numbers and ordinary text do not")
    void formulaInjection() {
        assertEquals("'=SUM(A1:A9)", ReportRules.safeCell("=SUM(A1:A9)"));
        assertEquals("'+27 82 000 0000", ReportRules.safeCell("+27 82 000 0000"));
        assertEquals("'-cmd|' /C calc'!A0", ReportRules.safeCell("-cmd|' /C calc'!A0"));
        assertEquals("'@SUM(1)", ReportRules.safeCell("@SUM(1)"));
        assertEquals("'\tx", ReportRules.safeCell("\tx"));
        assertEquals("-3", ReportRules.safeCell("-3"));
        assertEquals("-3.5", ReportRules.safeCell("-3.5"));
        assertEquals("Annual return", ReportRules.safeCell("Annual return"));
        assertEquals("", ReportRules.safeCell(null));
        assertEquals("", ReportRules.safeCell(""));
    }

    @Test @DisplayName("CSV cells are quoted when they contain a comma, quote or line break, and quotes are doubled")
    void quoting() {
        assertEquals("plain", ReportRules.csvCell("plain"));
        assertEquals("\"a,b\"", ReportRules.csvCell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", ReportRules.csvCell("say \"hi\""));
        assertEquals("\"line1\nline2\"", ReportRules.csvCell("line1\nline2"));
        assertEquals("\"'=1,2\"", ReportRules.csvCell("=1,2"), "guard first, then quote");
    }

    @Test @DisplayName("CSV has a header row, CRLF line ends, and one line per row")
    void csv() {
        String out = ReportRules.csv(List.of("Reference", "Title"), List.of(List.of("R-1", "Alpha, Inc"), List.of("R-2", "=bad")));
        assertEquals("Reference,Title\r\nR-1,\"Alpha, Inc\"\r\nR-2,'=bad\r\n", out);
        assertEquals("A\r\n", ReportRules.csv(List.of("A"), List.of()));
    }

    @Test @DisplayName("Text, date, yes/no and number helpers never return null and flatten line breaks")
    void helpers() {
        assertEquals("", ReportRules.text(null));
        assertEquals("a b", ReportRules.text("a\r\nb"));
        assertEquals("", ReportRules.date(null));
        assertEquals("2026-06-01", ReportRules.date(LocalDate.of(2026, 6, 1)));
        assertEquals("Yes", ReportRules.yesNo(true));
        assertEquals("", ReportRules.yesNo((Boolean) null));
        assertEquals("No", ReportRules.yesNo(Boolean.FALSE));
        assertEquals("", ReportRules.num(null));
        assertEquals("7", ReportRules.num(7));
    }

    @Test @DisplayName("Days overdue is zero until the day after the due date")
    void overdue() {
        LocalDate today = LocalDate.of(2026, 6, 10);
        assertEquals(0L, ReportRules.daysOverdue(today, today));
        assertEquals(1L, ReportRules.daysOverdue(today.minusDays(1), today));
        assertEquals(0L, ReportRules.daysOverdue(today.plusDays(3), today));
        assertEquals(0L, ReportRules.daysOverdue(null, today));
    }

    @Test @DisplayName("File name is lower-case with hyphens and the date")
    void fileName() {
        assertEquals("legal-compliance-capa-ageing-2026-06-10.csv", ReportRules.fileName("CAPA_AGEING", LocalDate.of(2026, 6, 10)));
    }
}
