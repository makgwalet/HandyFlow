package za.co.handyflow.platform.warehousing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhseBillingInvoiceTest {

    private WhseBillingInvoice newInvoice() {
        return WhseBillingInvoice.create(UUID.randomUUID(), UUID.randomUUID(), "WHI00001",
                LocalDate.now().minusMonths(1), LocalDate.now(), LocalDate.now(), LocalDate.now().plusDays(30),
                new BigDecimal("300.00"), new BigDecimal("150.00"), BigDecimal.ZERO);
    }

    @Test
    void createComputesSubtotalAndTotal() {
        WhseBillingInvoice invoice = newInvoice();
        assertEquals(new BigDecimal("450.00"), invoice.getSubtotal());
        assertEquals(new BigDecimal("450.00"), invoice.getTotal());
        assertEquals("DRAFT", invoice.getStatus());
    }

    @Test
    void balanceEqualsTotalMinusAmountPaid() {
        WhseBillingInvoice invoice = newInvoice();
        assertEquals(new BigDecimal("450.00"), invoice.balance());
        invoice.recordPayment(new BigDecimal("200.00"));
        assertEquals(new BigDecimal("250.00"), invoice.balance());
    }

    @Test
    void recordPaymentMovesToPartialThenPaid() {
        WhseBillingInvoice invoice = newInvoice();
        invoice.recordPayment(new BigDecimal("200.00"));
        assertEquals("PARTIAL", invoice.getStatus());
        invoice.recordPayment(new BigDecimal("250.00"));
        assertEquals("PAID", invoice.getStatus());
        assertNotNull(invoice.getPaidAt());
    }

    @Test
    void markSentRequiresDraft() {
        WhseBillingInvoice invoice = newInvoice();
        invoice.markSent();
        assertEquals("SENT", invoice.getStatus());
        assertThrows(IllegalStateException.class, invoice::markSent);
    }
}
