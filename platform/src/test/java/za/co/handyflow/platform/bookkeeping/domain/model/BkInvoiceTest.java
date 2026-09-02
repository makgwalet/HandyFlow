package za.co.handyflow.platform.bookkeeping.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BkInvoiceTest {

    private BkInvoice newInvoice(BigDecimal subtotal) {
        LocalDate issue = LocalDate.now();
        return BkInvoice.create(TenantId.generate(), UUID.randomUUID(), "INV-00001",
                issue.minusMonths(1), issue, issue, issue.plusDays(30), subtotal, BigDecimal.ZERO);
    }

    @Test
    void newInvoiceStartsDraftWithTotalEqualToSubtotalPlusVat() {
        BkInvoice invoice = newInvoice(new BigDecimal("1000.00"));
        assertEquals("DRAFT", invoice.getStatus());
        assertEquals(new BigDecimal("1000.00"), invoice.getTotal());
        assertEquals(BigDecimal.ZERO, invoice.getAmountPaid());
        assertEquals(new BigDecimal("1000.00"), invoice.balance());
        assertFalse(invoice.isPaid());
    }

    @Test
    void rejectsNegativeSubtotal() {
        TenantId tenantId = TenantId.generate();
        UUID clientId = UUID.randomUUID();
        LocalDate issue = LocalDate.now();
        assertThrows(IllegalArgumentException.class, () -> BkInvoice.create(tenantId, clientId, "INV-00002",
                issue.minusMonths(1), issue, issue, issue.plusDays(30), new BigDecimal("-10.00"), null));
    }

    @Test
    void markSentOnlyAllowedFromDraft() {
        BkInvoice invoice = newInvoice(new BigDecimal("500.00"));
        invoice.markSent();
        assertEquals("SENT", invoice.getStatus());
        assertNotNull(invoice.getSentAt());
        assertThrows(IllegalStateException.class, invoice::markSent);
    }

    @Test
    void partialPaymentMovesToPartialStatus() {
        BkInvoice invoice = newInvoice(new BigDecimal("1000.00"));
        invoice.recordPayment(new BigDecimal("400.00"));
        assertEquals("PARTIAL", invoice.getStatus());
        assertEquals(new BigDecimal("600.00"), invoice.balance());
        assertFalse(invoice.isPaid());
    }

    @Test
    void fullPaymentMovesToPaidStatusAndSetsPaidAt() {
        BkInvoice invoice = newInvoice(new BigDecimal("1000.00"));
        invoice.recordPayment(new BigDecimal("1000.00"));
        assertEquals("PAID", invoice.getStatus());
        assertTrue(invoice.isPaid());
        assertNotNull(invoice.getPaidAt());
        assertEquals(BigDecimal.ZERO, invoice.balance());
    }

    @Test
    void overpaymentStillMarksPaid() {
        BkInvoice invoice = newInvoice(new BigDecimal("1000.00"));
        invoice.recordPayment(new BigDecimal("1200.00"));
        assertEquals("PAID", invoice.getStatus());
        assertTrue(invoice.isPaid());
    }

    @Test
    void recordPaymentRejectsNonPositiveAmount() {
        BkInvoice invoice = newInvoice(new BigDecimal("1000.00"));
        assertThrows(IllegalArgumentException.class, () -> invoice.recordPayment(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> invoice.recordPayment(new BigDecimal("-50.00")));
    }

    @Test
    void isOverdueOnlyWhenUnpaidAndPastDueDate() {
        LocalDate issue = LocalDate.now().minusDays(40);
        BkInvoice invoice = BkInvoice.create(TenantId.generate(), UUID.randomUUID(), "INV-00003",
                issue.minusMonths(1), issue, issue, issue.plusDays(30), new BigDecimal("1000.00"), BigDecimal.ZERO);
        assertTrue(invoice.isOverdue(LocalDate.now()));
        invoice.recordPayment(new BigDecimal("1000.00"));
        assertFalse(invoice.isOverdue(LocalDate.now()));
    }
}
