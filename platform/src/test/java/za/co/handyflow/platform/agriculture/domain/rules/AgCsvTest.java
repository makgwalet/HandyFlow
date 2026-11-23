package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgCsvTest {

    @Test void plainTextIsLeftAlone() {
        assertEquals("Bessie", AgCsv.text("Bessie"));
        assertEquals("", AgCsv.text(null));
        assertEquals("", AgCsv.text("   "));
        assertEquals("AN-001", AgCsv.text("  AN-001 "));
    }

    @Test void commasQuotesAndLineBreaksAreQuoted() {
        assertEquals("\"Smith, J\"", AgCsv.text("Smith, J"));
        assertEquals("\"say \"\"hi\"\"\"", AgCsv.text("say \"hi\""));
        assertEquals("\"a\nb\"", AgCsv.text("a\nb"));
        assertEquals("\"a\rb\"", AgCsv.text("a\rb"));
    }

    @Test void textThatASpreadsheetWouldRunAsAFormulaIsDefused() {
        assertEquals("'=1+1", AgCsv.text("=1+1"));
        assertEquals("'+27821234567", AgCsv.text("+27821234567"));
        assertEquals("'-5", AgCsv.text("-5"));
        assertEquals("'@SUM(A1)", AgCsv.text("@SUM(A1)"));
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\")\"", AgCsv.text("=HYPERLINK(\"http://x\")"));
        assertEquals("a=b", AgCsv.text("a=b"), "only a leading character is dangerous");
    }

    @Test void numbersAndDatesStayNumbersAndDates() {
        assertEquals("1200.5", AgCsv.num(new BigDecimal("1200.50")));
        assertEquals("100", AgCsv.num(new BigDecimal("100.00")));
        assertEquals("-5", AgCsv.num(new BigDecimal("-5")));
        assertEquals("7", AgCsv.num(Integer.valueOf(7)));
        assertEquals("", AgCsv.num((Integer) null));
        assertEquals("", AgCsv.num((BigDecimal) null));
        assertEquals("2026-11-23", AgCsv.day(LocalDate.of(2026, 11, 23)));
        assertEquals("", AgCsv.day(null));
    }

    @Test void aLineEndsWithCrLf() {
        assertEquals("a,b,c\r\n", AgCsv.line(List.of("a", "b", "c")));
    }

    @Test void fileNamesHaveOnlySafeCharacters() {
        LocalDate d = LocalDate.of(2026, 11, 23);
        assertEquals("herd-register-Rietvlei-Farm-2026-11-23.csv", AgCsv.fileName("herd-register", "Rietvlei Farm", d, "csv"));
        assertEquals("treatments-2026-11-23.csv", AgCsv.fileName("treatments", null, d, "csv"));
        assertEquals("x-a-b-2026-11-23.pdf", AgCsv.fileName("x", "../a\"\r\nb", d, "pdf"));
        assertEquals("x-" + "a".repeat(40) + "-2026-11-23.csv", AgCsv.fileName("x", "a".repeat(41), d, "csv"));
        assertEquals("x-Farm-2026-11-23.csv", AgCsv.fileName("x", "Farm!! ", d, "csv"));
        assertEquals("x-" + "a".repeat(40) + "-2026-11-23.csv", AgCsv.fileName("x", "a".repeat(80), d, "csv"));
    }
}
