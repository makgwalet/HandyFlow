package za.co.handyflow.platform.warehousing.application.internal;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.warehousing.domain.model.WhseBillingInvoice;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhsePdfServiceInvoiceTest {

    private final WhsePdfService pdf = new WhsePdfService();

    private static WhseBillingInvoice invoice(String storage, String topUp) {
        WhseBillingInvoice inv = WhseBillingInvoice.create(UUID.randomUUID(), UUID.randomUUID(), "WH-0001",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1),
                new BigDecimal(storage), new BigDecimal("50.00"), new BigDecimal("150.00"));
        if (topUp != null) inv.recordMinimumTopUp(new BigDecimal(topUp));
        return inv;
    }

    @Test
    @DisplayName("with a top-up, storage is split into goods held and the minimum-charge top-up")
    void topUpIsItsOwnLine() {
        List<WhsePdfService.InvoiceLine> lines = WhsePdfService.invoiceLines(invoice("900.00", "620.00"));
        assertEquals("Storage of goods held", lines.get(0).label());
        assertEquals(0, new BigDecimal("280.00").compareTo(lines.get(0).amount()));
        assertTrue(lines.get(1).label().startsWith("Storage minimum charge top-up"));
        assertEquals(0, new BigDecimal("620.00").compareTo(lines.get(1).amount()));
        // the split never changes what is charged
        assertEquals(0, new BigDecimal("900.00").compareTo(lines.get(0).amount().add(lines.get(1).amount())));
    }

    @Test
    @DisplayName("pallet/bin fees are their own line and storage of goods is what is left")
    void positionFeesLine() {
        WhseBillingInvoice inv = invoice("900.00", "100.00");
        inv.recordLocationFees(new BigDecimal("300.00"));
        List<WhsePdfService.InvoiceLine> lines = WhsePdfService.invoiceLines(inv);
        assertEquals(0, new BigDecimal("500.00").compareTo(lines.get(0).amount()));
        assertEquals("Pallet / bin position fees", lines.get(1).label());
        assertEquals(0, new BigDecimal("300.00").compareTo(lines.get(1).amount()));
        assertTrue(lines.get(2).label().contains("top-up"));
    }

    @Test
    @DisplayName("with no top-up there is a single storage line and no top-up row")
    void noTopUp() {
        List<WhsePdfService.InvoiceLine> lines = WhsePdfService.invoiceLines(invoice("900.00", null));
        assertEquals("Storage", lines.get(0).label());
        assertTrue(lines.stream().noneMatch(l -> l.label().contains("top-up")));
        assertEquals("Total", lines.get(lines.size() - 1).label());
    }

    @Test
    @DisplayName("the PDF is produced and shows the top-up line and the draft warning")
    void pdfText() throws Exception {
        WhseClient client = WhseClient.create(UUID.randomUUID(), "Acme Traders", null, null, null, null, null, null, null, null, null);
        byte[] bytes = pdf.generateBillingInvoice("Cape Cold Store", "2019/123456/07", client, invoice("900.00", "620.00"));
        assertTrue(bytes.length > 500);
        PdfReader reader = new PdfReader(bytes);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        assertTrue(text.contains("WH-0001"));
        assertTrue(text.contains("Storage minimum charge top-up"));
        assertTrue(text.contains("620.00"));
        assertTrue(text.contains("NOT A TAX INVOICE"), "a DRAFT invoice must not read as an issued tax invoice");
    }
}
