package za.co.handyflow.platform.payrollbureau.domain.rules;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.payrollbureau.domain.rules.BankFileRules.Payment;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaymentFileRulesTest {

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void csvCellsAreEscapedAndFormulasNeutralised() {
        assertEquals("plain", CsvRules.cell("plain"));
        assertEquals("\"Smith, J\"", CsvRules.cell("Smith, J"));
        assertEquals("\"say \"\"hi\"\"\"", CsvRules.cell("say \"hi\""));
        assertEquals("'=SUM(A1)", CsvRules.cell("=SUM(A1)"));
        assertEquals("'-5", CsvRules.cell("-5"));
        assertEquals("a b", CsvRules.cell("a\nb"));
        assertEquals("", CsvRules.cell(null));
        assertEquals("1234.50", CsvRules.money(d("1234.5")));
        assertEquals("0.01", CsvRules.money(d("0.005")));
    }

    @Test
    void theBankFileHasOneLinePerPayableEmployeeAndTotals() {
        var r = BankFileRules.build(List.of(
                new Payment("E1", "Ann Dube", "FNB", "250655", "62000001", d("15000.456"), "PR1 E1"),
                new Payment("E2", "Bo Nel", "ABSA", "632005", "40000002", d("8000"), "PR1 E2")));
        assertEquals(2, r.paymentCount());
        assertEquals(0, d("23000.456").compareTo(r.total()));
        assertTrue(r.excluded().isEmpty());
        String[] lines = r.csv().split("\r\n");
        assertEquals(3, lines.length);
        assertEquals(BankFileRules.HEADER, lines[0]);
        assertEquals("E1,Ann Dube,FNB,250655,62000001,15000.46,PR1 E1", lines[1]);
    }

    @Test
    void employeesWithoutAnAccountOrNetPayAreLeftOutAndSaid() {
        var r = BankFileRules.build(List.of(
                new Payment("E1", "Cy Pillay", "FNB", "1", null, d("100"), "x"),
                new Payment("E2", "Di Moyo", "FNB", "1", "  ", d("100"), "x"),
                new Payment("E3", "Ed Zulu", "FNB", "1", "123", d("0"), "x"),
                new Payment("E4", "Fay Roux", "FNB", "1", "456", d("50"), "x")));
        assertEquals(1, r.paymentCount());
        assertEquals(3, r.excluded().size());
        assertEquals(0, d("50").compareTo(r.total()));
    }

    @Test
    void aSecondFileNeedsAReason() {
        assertNull(BankFileRules.supersedeProblem(false, null));
        assertNotNull(BankFileRules.supersedeProblem(true, null));
        assertNotNull(BankFileRules.supersedeProblem(true, " "));
        assertNull(BankFileRules.supersedeProblem(true, "Bank rejected the first file"));
    }

    private static JournalRules.Totals totals() {
        // earnings 25 000; PAYE 3 000; UIF 177.12 each side; SDL 250; medical 1 500; pension 1 000; other 400
        // net = 25 000 - 3 000 - 177.12 - 1 500 - 1 000 - 400 = 18 922.88
        return new JournalRules.Totals(d("25000"), d("3000"), d("177.12"), d("177.12"), d("250"), d("1500"), d("1000"), d("400"), d("18922.88"));
    }

    @Test
    void theJournalBalances() {
        var lines = JournalRules.lines(totals(), false);
        assertTrue(JournalRules.balanced(lines));
        assertEquals(0, d("25427.12").compareTo(lines.stream().map(JournalRules.Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    @Test
    void aReversalSwapsDebitsAndCreditsAndStillBalances() {
        var lines = JournalRules.lines(totals(), true);
        assertTrue(JournalRules.balanced(lines));
        var wages = lines.stream().filter(l -> l.account().startsWith("Salaries")).findFirst().orElseThrow();
        assertEquals(0, d("25000").compareTo(wages.credit()));
        assertEquals(0, wages.debit().signum());
    }

    @Test
    void zeroLinesAreLeftOut() {
        var t = new JournalRules.Totals(d("10000"), d("1000"), d("100"), d("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, d("8900"));
        var lines = JournalRules.lines(t, false);
        assertTrue(lines.stream().noneMatch(l -> l.account().startsWith("Skills") || l.account().startsWith("Medical")));
        assertTrue(JournalRules.balanced(lines));
    }

    @Test
    void theJournalCsvCarriesTheRunAndAmounts() {
        String csv = JournalRules.csv("PR00007", "1 Mar 2027 - 31 Mar 2027", "31 Mar 2027", false, JournalRules.lines(totals(), false));
        assertTrue(csv.startsWith("Pay run,PR00007\r\n"));
        assertTrue(csv.contains("Account,Debit,Credit\r\n"));
        assertTrue(csv.contains("PAYE payable (liability),0.00,3000.00\r\n"));
        assertFalse(csv.contains("Note,"));
        assertTrue(JournalRules.csv("PR7", "p", "d", true, JournalRules.lines(totals(), true)).contains("Note,Reversal"));
    }
}
