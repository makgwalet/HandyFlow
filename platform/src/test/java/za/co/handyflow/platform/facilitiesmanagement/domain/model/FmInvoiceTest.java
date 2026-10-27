package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FmInvoiceTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private FmInvoice newInvoice(BigDecimal subtotal) {
        return newInvoice(subtotal, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private FmInvoice newInvoice(BigDecimal subtotal, BigDecimal vat, BigDecimal vatRate) {
        LocalDate issue = LocalDate.now();
        return FmInvoice.create(TenantId.generate(), UUID.randomUUID(), "INV-00001",
                issue.minusMonths(1), issue, issue, issue.plusDays(30), subtotal, vat, vatRate, UUID.randomUUID(), null);
    }

    /** An invoice that has been approved and sent, so it can take payments and credit notes. */
    private FmInvoice sentInvoice(BigDecimal subtotal, BigDecimal vat, BigDecimal vatRate) {
        FmInvoice invoice = newInvoice(subtotal, vat, vatRate);
        invoice.approve(UUID.randomUUID());
        invoice.markSent();
        return invoice;
    }

    // ── creation ─────────────────────────────────────────────────────────────

    @Test
    void newInvoiceStartsDraftWithTotalEqualToSubtotalPlusVat() {
        FmInvoice invoice = newInvoice(bd("1000.00"), bd("150.00"), bd("15.00"));
        assertEquals("DRAFT", invoice.getStatus());
        assertEquals(bd("1150.00"), invoice.getTotal());
        assertEquals(BigDecimal.ZERO, invoice.getAmountPaid());
        assertEquals(bd("1150.00"), invoice.balance());
        assertEquals("NOT_POSTED", invoice.getGlStatus());
        assertFalse(invoice.isPaid());
    }

    @Test
    void rejectsNegativeSubtotal() {
        TenantId tenantId = TenantId.generate();
        UUID clientId = UUID.randomUUID();
        LocalDate issue = LocalDate.now();
        assertThrows(IllegalArgumentException.class, () -> FmInvoice.create(tenantId, clientId, "INV-00002",
                issue.minusMonths(1), issue, issue, issue.plusDays(30), bd("-10.00"), null, null, null, null));
    }

    // ── workflow ─────────────────────────────────────────────────────────────

    @Test
    void approvalMakesTheInvoiceEligibleForTheLedgerAndSending() {
        FmInvoice invoice = newInvoice(bd("500.00"));
        UUID approver = UUID.randomUUID();
        invoice.approve(approver);
        assertEquals("APPROVED", invoice.getStatus());
        assertEquals(approver, invoice.getApprovedByUserId());
        assertNotNull(invoice.getApprovedAt());
        assertEquals("PENDING", invoice.getGlStatus());
    }

    @Test
    void cannotSendAnUnapprovedDraft() {
        FmInvoice invoice = newInvoice(bd("500.00"));
        assertThrows(IllegalStateException.class, invoice::markSent);
    }

    @Test
    void markSentOnlyAllowedFromApproved() {
        FmInvoice invoice = newInvoice(bd("500.00"));
        invoice.approve(UUID.randomUUID());
        invoice.markSent();
        assertEquals("SENT", invoice.getStatus());
        assertNotNull(invoice.getSentAt());
        assertThrows(IllegalStateException.class, invoice::markSent);
    }

    @Test
    void onlyADraftCanBeCancelledAndItNeedsAReason() {
        FmInvoice draft = newInvoice(bd("500.00"));
        assertThrows(IllegalArgumentException.class, () -> draft.cancelDraft(" "));
        draft.cancelDraft("Wrong client");
        assertEquals("CANCELLED", draft.getStatus());
        assertEquals("Wrong client", draft.getCancellationReason());

        FmInvoice approved = newInvoice(bd("500.00"));
        approved.approve(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> approved.cancelDraft("too late"));
    }

    // ── payments ─────────────────────────────────────────────────────────────

    @Test
    void cannotRecordAPaymentBeforeTheInvoiceIsSent() {
        FmInvoice draft = newInvoice(bd("1000.00"));
        assertThrows(IllegalStateException.class, () -> draft.recordPayment(bd("100.00")));
        draft.approve(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> draft.recordPayment(bd("100.00")));
    }

    @Test
    void partialPaymentMovesToPartialStatus() {
        FmInvoice invoice = sentInvoice(bd("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        invoice.recordPayment(bd("400.00"));
        assertEquals("PARTIAL", invoice.getStatus());
        assertEquals(bd("600.00"), invoice.balance());
        assertFalse(invoice.isPaid());
    }

    @Test
    void fullPaymentMovesToPaidStatusAndSetsPaidAt() {
        FmInvoice invoice = sentInvoice(bd("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        invoice.recordPayment(bd("1000.00"));
        assertEquals("PAID", invoice.getStatus());
        assertTrue(invoice.isPaid());
        assertNotNull(invoice.getPaidAt());
        assertEquals(0, invoice.balance().signum());
    }

    @Test
    void overpaymentIsRejectedRatherThanSilentlyAccepted() {
        FmInvoice invoice = sentInvoice(bd("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> invoice.recordPayment(bd("1200.00")));
        assertEquals(BigDecimal.ZERO, invoice.getAmountPaid());
    }

    @Test
    void cannotPayAnInvoiceThatIsAlreadyPaid() {
        FmInvoice invoice = sentInvoice(bd("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        invoice.recordPayment(bd("1000.00"));
        assertThrows(IllegalStateException.class, () -> invoice.recordPayment(bd("1.00")));
    }

    @Test
    void recordPaymentRejectsNonPositiveAmount() {
        FmInvoice invoice = sentInvoice(bd("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> invoice.recordPayment(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> invoice.recordPayment(bd("-50.00")));
    }

    @Test
    void isOverdueOnlyWhenUnpaidAndPastDueDate() {
        LocalDate issue = LocalDate.now().minusDays(40);
        FmInvoice invoice = FmInvoice.create(TenantId.generate(), UUID.randomUUID(), "INV-00003",
                issue.minusMonths(1), issue, issue, issue.plusDays(30), bd("1000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, UUID.randomUUID(), null);
        assertFalse(invoice.isOverdue(LocalDate.now())); // a draft is never overdue
        invoice.approve(UUID.randomUUID());
        invoice.markSent();
        assertTrue(invoice.isOverdue(LocalDate.now()));
        invoice.recordPayment(bd("1000.00"));
        assertFalse(invoice.isOverdue(LocalDate.now()));
    }

    // ── credit ───────────────────────────────────────────────────────────────

    @Test
    void aCreditReducesTheBalanceAndAFullCreditSettlesAnUnpaidInvoice() {
        FmInvoice invoice = sentInvoice(bd("1000.00"), bd("150.00"), bd("15.00"));
        invoice.applyCredit(bd("230.00"));
        assertEquals(bd("920.00"), invoice.balance());
        assertEquals("SENT", invoice.getStatus());

        invoice.applyCredit(bd("920.00"));
        assertEquals(0, invoice.balance().signum());
        assertEquals("CREDITED", invoice.getStatus());
    }

    @Test
    void creditingTheRemainderOfAPartPaidInvoiceMakesItPaid() {
        FmInvoice invoice = sentInvoice(bd("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        invoice.recordPayment(bd("600.00"));
        invoice.applyCredit(bd("400.00"));
        assertEquals("PAID", invoice.getStatus());
        assertNotNull(invoice.getPaidAt());
    }

    @Test
    void cannotCreditMoreThanIsOutstanding() {
        FmInvoice invoice = sentInvoice(bd("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        invoice.recordPayment(bd("700.00"));
        assertThrows(IllegalArgumentException.class, () -> invoice.applyCredit(bd("300.01")));
        assertEquals(bd("300.00"), invoice.balance());
    }

    @Test
    void cannotCreditADraftCancelledPaidOrAlreadyCreditedInvoice() {
        assertThrows(IllegalStateException.class, () -> newInvoice(bd("100.00")).applyCredit(bd("10.00")));

        FmInvoice cancelled = newInvoice(bd("100.00"));
        cancelled.cancelDraft("oops");
        assertThrows(IllegalStateException.class, () -> cancelled.applyCredit(bd("10.00")));

        FmInvoice paid = sentInvoice(bd("100.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        paid.recordPayment(bd("100.00"));
        assertThrows(IllegalStateException.class, () -> paid.applyCredit(bd("10.00")));

        FmInvoice credited = sentInvoice(bd("100.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        credited.applyCredit(bd("100.00"));
        assertThrows(IllegalStateException.class, () -> credited.applyCredit(bd("10.00")));
    }

    @Test
    void aFullyCreditedInvoiceCannotBePaid() {
        FmInvoice invoice = sentInvoice(bd("100.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        invoice.applyCredit(bd("100.00"));
        assertThrows(IllegalStateException.class, () -> invoice.recordPayment(bd("1.00")));
    }

    @Test
    void creditAmountMustBePositive() {
        FmInvoice invoice = sentInvoice(bd("100.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> invoice.applyCredit(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> invoice.applyCredit(bd("-1")));
        assertThrows(IllegalArgumentException.class, () -> invoice.applyCredit(null));
    }

    // ── tax-invoice rules ────────────────────────────────────────────────────

    @Test
    void anInvoiceFromANonVatVendorIsNotATaxInvoiceAndNeedsNoPartyDetails() {
        FmInvoice invoice = newInvoice(bd("9000.00"));
        invoice.snapshotParties("Acme FM", null, null, null, null, null);
        assertFalse(invoice.isTaxInvoice());
        invoice.approve(UUID.randomUUID()); // does not throw
        assertEquals("APPROVED", invoice.getStatus());
    }

    @Test
    void aVatVendorMustShowItsOwnNameAndAddress() {
        FmInvoice invoice = newInvoice(bd("1000.00"), bd("150.00"), bd("15.00"));
        invoice.snapshotParties("Acme FM", "4123456789", null, "Client", null, "1 Main Rd");
        assertTrue(invoice.isTaxInvoice());
        assertThrows(IllegalStateException.class, () -> invoice.approve(UUID.randomUUID()));
        assertEquals("DRAFT", invoice.getStatus());
    }

    @Test
    void aTaxInvoiceAboveR5000MustShowTheClientNameAndAddress() {
        FmInvoice big = newInvoice(bd("5000.00"), bd("750.00"), bd("15.00")); // total 5750
        big.snapshotParties("Acme FM", "4123456789", "9 Supplier St", "Client", null, null);
        assertThrows(IllegalStateException.class, () -> big.approve(UUID.randomUUID()));
        big.snapshotParties("Acme FM", "4123456789", "9 Supplier St", "Client", null, "1 Main Rd");
        big.approve(UUID.randomUUID());
        assertEquals("APPROVED", big.getStatus());
    }

    @Test
    void aSmallTaxInvoiceDoesNotNeedTheClientAddress() {
        FmInvoice small = newInvoice(bd("1000.00"), bd("150.00"), bd("15.00")); // total 1150
        small.snapshotParties("Acme FM", "4123456789", "9 Supplier St", "Client", null, null);
        small.approve(UUID.randomUUID());
        assertEquals("APPROVED", small.getStatus());
    }

    @Test
    void theThresholdIsExclusive() {
        FmInvoice exactly = newInvoice(bd("4347.83"), bd("652.17"), bd("15.00")); // total exactly 5000.00
        assertEquals(0, exactly.getTotal().compareTo(FmInvoice.RECIPIENT_DETAILS_THRESHOLD));
        exactly.snapshotParties("Acme FM", "4123456789", "9 Supplier St", "Client", null, null);
        exactly.approve(UUID.randomUUID());
        assertEquals("APPROVED", exactly.getStatus());
    }

    @Test
    void partiesCannotChangeOnceTheInvoiceHasBeenIssued() {
        FmInvoice invoice = newInvoice(bd("100.00"));
        invoice.approve(UUID.randomUUID());
        assertThrows(IllegalStateException.class,
                () -> invoice.snapshotParties("X", null, null, "Y", null, null));
    }

    // ── ledger posting state ─────────────────────────────────────────────────

    @Test
    void ledgerPostingStatesMoveFromPendingToPostedOrFailedAndBack() {
        FmInvoice invoice = newInvoice(bd("100.00"));
        invoice.approve(UUID.randomUUID());
        assertFalse(invoice.isInLedger());

        invoice.markGlFailed("no such account");
        assertEquals("FAILED", invoice.getGlStatus());
        assertEquals(1, invoice.getGlAttempts());

        invoice.resetGlRetry();
        assertEquals("PENDING", invoice.getGlStatus());
        assertEquals(0, invoice.getGlAttempts());

        UUID entry = UUID.randomUUID();
        invoice.markGlPosted(entry);
        assertEquals("POSTED", invoice.getGlStatus());
        assertEquals(entry, invoice.getGlEntryId());
        assertTrue(invoice.isInLedger());
    }
}
