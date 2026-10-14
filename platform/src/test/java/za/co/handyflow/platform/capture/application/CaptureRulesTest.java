package za.co.handyflow.platform.capture.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

class CaptureRulesTest {
    int fails = 0;
    void eq(String what, Object want, Object got) {
        if (want == null ? got != null : !want.equals(got)) { fails++; System.out.println("FAIL " + what + " want=" + want + " got=" + got); }
    }
    @Test
    void readsFieldsFromTypicalDocuments() {
        LocalDate today = LocalDate.of(2026, 10, 14);
        eq("amt1", new BigDecimal("1234.56"), CaptureRules.amount("1 234,56"));
        eq("amt2", new BigDecimal("1234.56"), CaptureRules.amount("1,234.56"));
        eq("amt3", new BigDecimal("1234.56"), CaptureRules.amount("R1234.56"));
        eq("amt4", new BigDecimal("115.00"), CaptureRules.amount("115"));
        eq("amt5", null, CaptureRules.amount(""));
        eq("d1", LocalDate.of(2026, 3, 5), CaptureRules.date("05/03/2026"));
        eq("d2", LocalDate.of(2026, 3, 5), CaptureRules.date("2026-03-05"));
        eq("d3", LocalDate.of(2026, 3, 5), CaptureRules.date("5 March 2026"));
        eq("d4", null, CaptureRules.date("31/02/2026"));

        String inv = """
                ACME Plumbing Supplies (Pty) Ltd
                TAX INVOICE
                VAT No: 4123456789
                Invoice No: INV-2026-0042
                Invoice Date: 01/10/2026
                Due Date: 31/10/2026
                Pipe 15mm  2   R 400.00
                Subtotal            R 800.00
                VAT 15%             R 120.00
                Total               R 920.00
                """;
        var f = CaptureRules.parse(inv, today);
        eq("sup", "ACME Plumbing Supplies (Pty) Ltd", f.supplierName());
        eq("no", "INV-2026-0042", f.documentNumber());
        eq("date", LocalDate.of(2026, 10, 1), f.documentDate());
        eq("due", LocalDate.of(2026, 10, 31), f.dueDate());
        eq("total", new BigDecimal("920.00"), f.total());
        eq("vat", new BigDecimal("120.00"), f.vatAmount());
        eq("vatno", "4123456789", f.supplierVatNumber());

        var r = CaptureRules.parse("Corner Cafe\nSlip 8841\n2026-10-12\nCoffee 45,00\nTOTAL R 45,00\n", today);
        eq("r.sup", "Corner Cafe", r.supplierName());
        eq("r.total", new BigDecimal("45.00"), r.total());
        eq("r.date", LocalDate.of(2026, 10, 12), r.documentDate());

        var bad = CaptureRules.parse("Shop\nTotal R 100.00\nVAT R 40.00\n", today);
        eq("bad.note", true, bad.notes().stream().anyMatch(n -> n.contains("15%")));

        var future = CaptureRules.parse("Shop\nDate: 01/01/2027\nTotal R 10.00\n", today);
        eq("fut", true, future.notes().stream().anyMatch(n -> n.contains("future")));

        var empty = CaptureRules.parse("  ", today);
        eq("empty", null, empty.total());
        eq("empty.note", true, empty.notes().get(0).contains("No text"));

        if (fails > 0) throw new AssertionError(fails + " failed");
    }
}
