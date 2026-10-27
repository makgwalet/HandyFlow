package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FacilityCsvTest {

    @Test
    void startsWithAByteOrderMarkAndUsesCrlf() {
        String csv = FacilityCsv.build(List.of("A", "B"), List.of(List.of("x", "y")));
        assertEquals("\uFEFFA,B\r\nx,y\r\n", csv);
    }

    @Test
    void quotesCommasQuotesAndNewlines() {
        assertEquals("\"a,b\"", FacilityCsv.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", FacilityCsv.cell("say \"hi\""));
        assertEquals("\"line1\nline2\"", FacilityCsv.cell("line1\nline2"));
        assertEquals("plain", FacilityCsv.cell("plain"));
    }

    @Test
    void neutralisesTextASpreadsheetWouldRunAsAFormula() {
        assertEquals("'=SUM(A1)", FacilityCsv.cell("=SUM(A1)"));
        assertEquals("'+27821234567", FacilityCsv.cell("+27821234567"));
        assertEquals("'-2+3", FacilityCsv.cell("-2+3"));
        assertEquals("'@cmd", FacilityCsv.cell("@cmd"));
        assertEquals("\"'=HYPERLINK(\"\"x\"\",\"\"y\"\")\"", FacilityCsv.cell("=HYPERLINK(\"x\",\"y\")"));
    }

    @Test
    void numbersStayNumbersEvenWhenNegative() {
        assertEquals("-5", FacilityCsv.cell(-5L));
        assertEquals("-1.50", FacilityCsv.cell(new BigDecimal("-1.50")));
        assertEquals("1000", FacilityCsv.cell(new BigDecimal("1E+3")));
        assertEquals("true", FacilityCsv.cell(true));
    }

    @Test
    void nullsAreEmptyAndDatesAreIso() {
        assertEquals("", FacilityCsv.cell(null));
        assertEquals("2026-10-27", FacilityCsv.cell(LocalDate.of(2026, 10, 27)));
        assertEquals("h\r\na,,c\r\n", FacilityCsv.build(List.of("h"), List.of(Arrays.asList("a", null, "c"))).substring(1));
    }
}
