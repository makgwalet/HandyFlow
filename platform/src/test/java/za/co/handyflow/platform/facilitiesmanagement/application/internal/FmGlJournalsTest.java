package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest.JournalLineRequest;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmCreditNote;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoicePayment;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The double-entry rules for every facilities-management document: each journal must balance, and hit the right side of the right account. */
class FmGlJournalsTest {

    private static final UUID AR = UUID.randomUUID();
    private static final UUID REVENUE = UUID.randomUUID();
    private static final UUID VAT = UUID.randomUUID();
    private static final UUID BANK = UUID.randomUUID();

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private FmInvoice sentInvoice(String subtotal, String vat, String rate) {
        LocalDate issue = LocalDate.now();
        FmInvoice invoice = FmInvoice.create(TenantId.generate(), UUID.randomUUID(), "FMINV-0001",
                issue.minusMonths(1), issue, issue, issue.plusDays(30), bd(subtotal), bd(vat), bd(rate), UUID.randomUUID(), null);
        invoice.approve(UUID.randomUUID());
        invoice.markSent();
        return invoice;
    }

    private static BigDecimal debits(List<JournalLineRequest> lines) {
        return lines.stream().map(JournalLineRequest::debitAmount).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal credits(List<JournalLineRequest> lines) {
        return lines.stream().map(JournalLineRequest::creditAmount).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static JournalLineRequest line(List<JournalLineRequest> lines, UUID account) {
        return lines.stream().filter(l -> l.accountId().equals(account)).findFirst().orElseThrow();
    }

    // ── invoice ──────────────────────────────────────────────────────────────

    @Test
    void anInvoiceDebitsReceivablesAndCreditsRevenueAndVat() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        List<JournalLineRequest> lines = FmGlJournals.invoiceLines(invoice, "Client Co", AR, REVENUE, VAT);
        assertEquals(3, lines.size());
        assertEquals(bd("1150.00"), line(lines, AR).debitAmount());
        assertEquals(bd("1000.00"), line(lines, REVENUE).creditAmount());
        assertEquals(bd("150.00"), line(lines, VAT).creditAmount());
        assertEquals(0, debits(lines).compareTo(credits(lines)));
    }

    @Test
    void anInvoiceWithoutVatHasNoVatLine() {
        FmInvoice invoice = sentInvoice("1000.00", "0.00", "0.00");
        List<JournalLineRequest> lines = FmGlJournals.invoiceLines(invoice, "Client Co", AR, REVENUE, null);
        assertEquals(2, lines.size());
        assertEquals(0, debits(lines).compareTo(credits(lines)));
    }

    @Test
    void anInvoiceWithVatCannotPostWithoutAVatAccount() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        assertThrows(IllegalStateException.class, () -> FmGlJournals.invoiceLines(invoice, "Client Co", AR, REVENUE, null));
    }

    // ── credit note ──────────────────────────────────────────────────────────

    @Test
    void aCreditNoteIsTheMirrorImageOfTheInvoice() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        FmCreditNote note = FmCreditNote.forSubtotal(invoice, () -> "FMCN-0001", LocalDate.now(), bd("200.00"), "Overbilled", UUID.randomUUID());
        List<JournalLineRequest> lines = FmGlJournals.creditNoteLines(note, invoice, "Client Co", AR, REVENUE, VAT);
        assertEquals(3, lines.size());
        assertEquals(bd("200.00"), line(lines, REVENUE).debitAmount());
        assertEquals(bd("30.00"), line(lines, VAT).debitAmount());
        assertEquals(bd("230.00"), line(lines, AR).creditAmount());
        assertNull(line(lines, AR).debitAmount());
        assertEquals(0, debits(lines).compareTo(credits(lines)));
    }

    @Test
    void aFullBalanceCreditNoteStillBalancesToTheCent() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        invoice.recordPayment(bd("500.00"));
        FmCreditNote note = FmCreditNote.forFullBalance(invoice, () -> "FMCN-0002", LocalDate.now(), "Dispute settled", UUID.randomUUID());
        List<JournalLineRequest> lines = FmGlJournals.creditNoteLines(note, invoice, "Client Co", AR, REVENUE, VAT);
        assertEquals(0, debits(lines).compareTo(credits(lines)));
        assertEquals(bd("650.00"), line(lines, AR).creditAmount());
    }

    @Test
    void aCreditNoteWithoutVatHasNoVatLine() {
        FmInvoice invoice = sentInvoice("1000.00", "0.00", "0.00");
        FmCreditNote note = FmCreditNote.forSubtotal(invoice, () -> "FMCN-0003", LocalDate.now(), bd("100.00"), "x", UUID.randomUUID());
        List<JournalLineRequest> lines = FmGlJournals.creditNoteLines(note, invoice, "Client Co", AR, REVENUE, null);
        assertEquals(2, lines.size());
        assertEquals(0, debits(lines).compareTo(credits(lines)));
    }

    // ── payment ──────────────────────────────────────────────────────────────

    @Test
    void aPaymentDebitsTheBankAndCreditsReceivables() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        FmInvoicePayment payment = FmInvoicePayment.create(invoice.getTenantId(), invoice.getId(), bd("400.00"), null, "EFT", "EFT-77", UUID.randomUUID());
        List<JournalLineRequest> lines = FmGlJournals.paymentLines(payment, invoice, "Client Co", BANK, AR);
        assertEquals(2, lines.size());
        assertEquals(bd("400.00"), line(lines, BANK).debitAmount());
        assertEquals(bd("400.00"), line(lines, AR).creditAmount());
        assertTrue(line(lines, BANK).description().contains("EFT-77"));
        assertEquals(0, debits(lines).compareTo(credits(lines)));
    }
}
