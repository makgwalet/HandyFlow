package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FmCreditNoteTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private FmInvoice sentInvoice(String subtotal, String vat, String rate) {
        LocalDate issue = LocalDate.now();
        FmInvoice invoice = FmInvoice.create(TenantId.generate(), UUID.randomUUID(), "FMINV-0001",
                issue.minusMonths(1), issue, issue, issue.plusDays(30), bd(subtotal), bd(vat), bd(rate), UUID.randomUUID(), null);
        invoice.snapshotParties("Acme FM", "4123456789", "9 Supplier St", "Client Co", "4987654321", "1 Main Rd");
        invoice.approve(UUID.randomUUID());
        invoice.markSent();
        return invoice;
    }

    private static final java.util.function.Supplier<String> NUMBER = () -> "FMCN-0001";

    @Test
    void creditingASubtotalAddsVatAtTheInvoicesRate() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        FmCreditNote note = FmCreditNote.forSubtotal(invoice, NUMBER, LocalDate.now(), bd("200.00"), "Overbilled call-out", UUID.randomUUID());
        assertEquals(bd("200.00"), note.getSubtotal());
        assertEquals(bd("30.00"), note.getVatAmount());
        assertEquals(bd("230.00"), note.getTotal());
        assertEquals(bd("15.00"), note.getVatRate());
        assertEquals("DRAFT", note.getStatus());
        assertEquals("NOT_POSTED", note.getGlStatus());
        assertEquals("FMCN-0001", note.getCreditNoteNumber());
        assertEquals(invoice.getId(), note.getInvoiceId());
    }

    @Test
    void vatIsRoundedHalfUpToTheCent() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        FmCreditNote note = FmCreditNote.forSubtotal(invoice, NUMBER, LocalDate.now(), bd("33.33"), "x", UUID.randomUUID());
        assertEquals(bd("5.00"), note.getVatAmount()); // 4.9995
        assertEquals(bd("38.33"), note.getTotal());
    }

    @Test
    void aCreditOnANonVatInvoiceCarriesNoVat() {
        FmInvoice invoice = sentInvoice("1000.00", "0.00", "0.00");
        FmCreditNote note = FmCreditNote.forSubtotal(invoice, NUMBER, LocalDate.now(), bd("100.00"), "x", UUID.randomUUID());
        assertEquals(0, note.getVatAmount().signum());
        assertEquals(bd("100.00"), note.getTotal());
        assertEquals(bd("0.00"), note.getVatRate().setScale(2));
    }

    @Test
    void creditingTheFullBalanceBacksVatOutOfTheTotalSoItSettlesToTheCent() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00"); // total 1150
        FmCreditNote note = FmCreditNote.forFullBalance(invoice, NUMBER, LocalDate.now(), "Cancelled contract", UUID.randomUUID());
        assertEquals(bd("1150.00"), note.getTotal());
        assertEquals(bd("150.00"), note.getVatAmount());
        assertEquals(bd("1000.00"), note.getSubtotal());
        invoice.applyCredit(note.getTotal());
        assertEquals("CREDITED", invoice.getStatus());
    }

    @Test
    void fullBalanceCreditAfterAPartPaymentCreditsOnlyWhatIsOutstanding() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        invoice.recordPayment(bd("500.00"));
        FmCreditNote note = FmCreditNote.forFullBalance(invoice, NUMBER, LocalDate.now(), "Dispute settled", UUID.randomUUID());
        assertEquals(bd("650.00"), note.getTotal());
        assertEquals(bd("84.78"), note.getVatAmount()); // 650 x 15 / 115 = 84.7826
        assertEquals(bd("565.22"), note.getSubtotal());
        assertEquals(note.getTotal(), note.getSubtotal().add(note.getVatAmount()));
    }

    @Test
    void cannotCreditMoreThanTheOutstandingBalance() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        assertThrows(IllegalArgumentException.class,
                () -> FmCreditNote.forSubtotal(invoice, NUMBER, LocalDate.now(), bd("1000.01"), "x", UUID.randomUUID()));
    }

    @Test
    void aFullyCreditedInvoiceHasNothingLeftToCredit() {
        FmInvoice invoice = sentInvoice("100.00", "0.00", "0.00");
        invoice.applyCredit(bd("100.00"));
        assertThrows(IllegalStateException.class,
                () -> FmCreditNote.forFullBalance(invoice, NUMBER, LocalDate.now(), "x", UUID.randomUUID()));
    }

    @Test
    void aReasonIsRequiredAndBoundedTo500Characters() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        assertThrows(IllegalArgumentException.class,
                () -> FmCreditNote.forSubtotal(invoice, NUMBER, LocalDate.now(), bd("10.00"), " ", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> FmCreditNote.forSubtotal(invoice, NUMBER, LocalDate.now(), bd("10.00"), null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> FmCreditNote.forSubtotal(invoice, NUMBER, LocalDate.now(), bd("10.00"), "x".repeat(501), UUID.randomUUID()));
    }

    @Test
    void theAmountMustBePositive() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        assertThrows(IllegalArgumentException.class,
                () -> FmCreditNote.forSubtotal(invoice, NUMBER, LocalDate.now(), BigDecimal.ZERO, "x", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> FmCreditNote.forSubtotal(invoice, NUMBER, LocalDate.now(), null, "x", UUID.randomUUID()));
    }

    @Test
    void onlyAnIssuedOpenInvoiceCanBeCredited() {
        LocalDate issue = LocalDate.now();
        FmInvoice draft = FmInvoice.create(TenantId.generate(), UUID.randomUUID(), "FMINV-0002",
                issue.minusMonths(1), issue, issue, issue.plusDays(30), bd("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, UUID.randomUUID(), null);
        assertThrows(IllegalStateException.class,
                () -> FmCreditNote.forSubtotal(draft, NUMBER, LocalDate.now(), bd("10.00"), "x", UUID.randomUUID()));
    }

    @Test
    void aRejectedCreditNoteDoesNotUseUpANumberFromTheSeries() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        AtomicInteger issued = new AtomicInteger();
        java.util.function.Supplier<String> counting = () -> "FMCN-" + issued.incrementAndGet();
        assertThrows(IllegalArgumentException.class,
                () -> FmCreditNote.forSubtotal(invoice, counting, LocalDate.now(), bd("999999.00"), "x", UUID.randomUUID()));
        assertEquals(0, issued.get());
        FmCreditNote ok = FmCreditNote.forSubtotal(invoice, counting, LocalDate.now(), bd("10.00"), "x", UUID.randomUUID());
        assertEquals("FMCN-1", ok.getCreditNoteNumber());
    }

    @Test
    void thePartiesAreCopiedFromTheInvoiceSnapshot() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        FmCreditNote note = FmCreditNote.forSubtotal(invoice, NUMBER, LocalDate.now(), bd("10.00"), "x", UUID.randomUUID());
        assertEquals("Acme FM", note.getSupplierName());
        assertEquals("4123456789", note.getSupplierVatNumber());
        assertEquals("Client Co", note.getClientName());
        assertEquals("4987654321", note.getClientVatNumber());
        assertTrue(note.isTaxCreditNote());
    }

    @Test
    void approvalQueuesTheLedgerPostingAndHappensOnce() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        FmCreditNote note = FmCreditNote.forSubtotal(invoice, NUMBER, LocalDate.now(), bd("10.00"), "x", UUID.randomUUID());
        UUID approver = UUID.randomUUID();
        note.approve(approver);
        assertEquals("APPROVED", note.getStatus());
        assertEquals(approver, note.getApprovedByUserId());
        assertNotNull(note.getApprovedAt());
        assertEquals("PENDING", note.getGlStatus());
        assertThrows(IllegalStateException.class, () -> note.approve(approver));
    }

    @Test
    void aDraftCanBeCancelledWithAReasonButAnApprovedOneCannot() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        FmCreditNote draft = FmCreditNote.forSubtotal(invoice, NUMBER, LocalDate.now(), bd("10.00"), "x", UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> draft.cancelDraft(" "));
        draft.cancelDraft("Raised in error");
        assertEquals("CANCELLED", draft.getStatus());
        assertThrows(IllegalStateException.class, () -> draft.approve(UUID.randomUUID()));

        FmCreditNote approved = FmCreditNote.forSubtotal(invoice, NUMBER, LocalDate.now(), bd("10.00"), "x", UUID.randomUUID());
        approved.approve(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> approved.cancelDraft("too late"));
    }

    @Test
    void ledgerPostingFailuresAreRecordedAndCanBeRequeued() {
        FmInvoice invoice = sentInvoice("1000.00", "150.00", "15.00");
        FmCreditNote note = FmCreditNote.forSubtotal(invoice, NUMBER, LocalDate.now(), bd("10.00"), "x", UUID.randomUUID());
        note.approve(UUID.randomUUID());
        note.markGlFailed("x".repeat(600));
        assertEquals("FAILED", note.getGlStatus());
        assertEquals(500, note.getGlLastError().length());
        note.resetGlRetry();
        assertEquals("PENDING", note.getGlStatus());
        assertEquals(0, note.getGlAttempts());
        note.markGlPosted(UUID.randomUUID());
        assertEquals("POSTED", note.getGlStatus());
    }
}
