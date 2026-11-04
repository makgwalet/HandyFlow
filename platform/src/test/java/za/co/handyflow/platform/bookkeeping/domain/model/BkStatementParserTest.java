package za.co.handyflow.platform.bookkeeping.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BkStatementParserTest {

    private static final String H = "Date,Description,Reference,Amount\n";

    @Test void readsIsoAndDayMonthYearDates() {
        var rows = BkStatementParser.parse(H + "2026-10-02,ENGEN,POS 1,-700.00\n05/09/2026,RENT,R1,8500\n");
        assertEquals(LocalDate.of(2026, 10, 2), rows.get(0).date());
        assertEquals(LocalDate.of(2026, 9, 5), rows.get(1).date());
        assertEquals(new BigDecimal("-700.00"), rows.get(0).amount());
    }

    @Test void impossibleDateNamesTheLine() {
        var e = assertThrows(BkStatementParser.StatementException.class,
                () -> BkStatementParser.parse(H + "2026-10-02,A,B,1\n31/09/2026,A,B,1\n"));
        assertTrue(e.getMessage().contains("Line 3"));
        assertTrue(e.getMessage().contains("'31/09/2026' is not a real date"));
        assertTrue(e.getMessage().startsWith("Nothing was imported."));
    }

    @Test void badAmountIsPlainWords() {
        var e = assertThrows(BkStatementParser.StatementException.class, () -> BkStatementParser.parse(H + "2026-10-02,A,B,12x\n"));
        assertTrue(e.getMessage().contains("'12x' is not an amount"));
        assertFalse(e.getMessage().contains("decimal digit"));
    }

    @Test void shortRowIsReported() {
        var e = assertThrows(BkStatementParser.StatementException.class, () -> BkStatementParser.parse(H + "2026-10-02,only two\n"));
        assertTrue(e.getMessage().contains("Line 2: expected Date, Description, Reference, Amount."));
    }

    @Test void quotedCommasStayInOneColumn() {
        var rows = BkStatementParser.parse(H + "2026-10-02,\"SMITH, J\",REF,-10\n");
        assertEquals("SMITH, J", rows.get(0).description());
    }

    @Test void onlyFirstFiveProblemsAreShown() {
        StringBuilder sb = new StringBuilder(H);
        for (int i = 0; i < 8; i++) sb.append("bad,A,B,1\n");
        var e = assertThrows(BkStatementParser.StatementException.class, () -> BkStatementParser.parse(sb.toString()));
        assertEquals(8, e.getProblems().size());
        assertTrue(e.getMessage().contains("(and 3 more)"));
    }

    @Test void blankLinesAndBomAreIgnored() {
        List<BkStatementParser.Row> rows = BkStatementParser.parse("﻿" + H + "\n2026-10-02,A,B,1\n\n");
        assertEquals(1, rows.size());
    }

    @Test void hugeAmountRefused() {
        assertThrows(BkStatementParser.StatementException.class, () -> BkStatementParser.parse(H + "2026-10-02,A,B,99999999999\n"));
    }
}
