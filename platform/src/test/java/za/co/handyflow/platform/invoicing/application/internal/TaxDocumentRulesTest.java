package za.co.handyflow.platform.invoicing.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import za.co.handyflow.platform.invoicing.application.internal.TaxDocumentRules.*;

/** Finance patch F14: tax invoice and credit note content. */
class TaxDocumentRulesTest {
    static BigDecimal d(String v) { return new BigDecimal(v); }
    Map<String, String> addr = Map.of("street", "1 Main Rd", "city", "Cape Town", "postalCode", "8001");

    @Test void headings() {
        assertEquals("TAX INVOICE", TaxDocumentRules.invoiceHeading(true, true));
        assertEquals("INVOICE", TaxDocumentRules.invoiceHeading(true, false));
        assertEquals("DRAFT INVOICE", TaxDocumentRules.invoiceHeading(false, true));
        assertEquals("CREDIT NOTE", TaxDocumentRules.creditNoteHeading(true));
        assertEquals("CREDIT NOTE (NOT YET ISSUED)", TaxDocumentRules.creditNoteHeading(false));
    }

    @Test void vatNumbers() {
        assertTrue(TaxDocumentRules.looksLikeVatNumber("4123456789"));
        assertTrue(TaxDocumentRules.looksLikeVatNumber("412 345 6789"));
        assertFalse(TaxDocumentRules.looksLikeVatNumber("1234567890"));
        assertFalse(TaxDocumentRules.looksLikeVatNumber("41234567"));
        assertFalse(TaxDocumentRules.looksLikeVatNumber(null));
    }

    @Test void mixedRatesAreShownSeparately() {
        List<RateTotal> t = TaxDocumentRules.byRate(List.of(
                new LineAmount(d("15.00"), d("100.00"), d("15.00")),
                new LineAmount(d("0.00"), d("50.00"), d("0.00")),
                new LineAmount(d("15"), d("200.00"), d("30.00"))));
        assertEquals(2, t.size());
        assertEquals(0, t.get(0).rate().compareTo(BigDecimal.ZERO));
        assertEquals(d("50.00"), t.get(0).base());
        assertEquals(0, t.get(1).rate().compareTo(d("15")));
        assertEquals(d("300.00"), t.get(1).base());
        assertEquals(d("45.00"), t.get(1).vat());
    }

    @Test void addressText() {
        assertEquals("1 Main Rd, Cape Town, 8001", TaxDocumentRules.addressText(addr));
        assertEquals("", TaxDocumentRules.addressText(null));
        assertEquals("", TaxDocumentRules.addressText(Map.of("street", " ")));
    }

    @Test void supplierIssues() {
        assertTrue(TaxDocumentRules.supplierIssues("4123456789", addr, "Acme").isEmpty());
        List<Issue> none = TaxDocumentRules.supplierIssues(null, null, "Acme");
        assertEquals(2, none.size());
        assertEquals("ERROR", none.get(0).severity());      // address
        assertEquals("WARNING", none.get(1).severity());    // no VAT number
        assertEquals(1, TaxDocumentRules.supplierIssues("999", addr, "Acme").size());
        assertEquals("ERROR", TaxDocumentRules.supplierIssues("4123456789", addr, " ").get(0).severity());
    }

    @Test void recipientDetailsOnlyMatterAboveFiveThousand() {
        assertTrue(TaxDocumentRules.recipientIssues(d("5000.00"), null, null, null).isEmpty());
        List<Issue> big = TaxDocumentRules.recipientIssues(d("5000.01"), "Cust", null, null);
        assertEquals(2, big.size());
        assertTrue(big.get(0).message().contains("address"));
        assertEquals("INFO", big.get(1).severity());
        assertEquals(0, TaxDocumentRules.recipientIssues(d("9000"), "Cust", addr, "4123456789").size());
    }

    @Test void creditNoteNeedsAReason() {
        assertNotNull(TaxDocumentRules.creditNoteProblem(null));
        assertNotNull(TaxDocumentRules.creditNoteProblem("  "));
        assertNull(TaxDocumentRules.creditNoteProblem("Goods returned"));
    }
}
