package za.co.handyflow.platform.trainingprovider.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrainProvInvoiceTest {

    private TrainProvInvoice newInvoice() {
        return TrainProvInvoice.create(TenantId.generate(), UUID.randomUUID(), "TPI-00001",
                LocalDate.now().minusMonths(1), LocalDate.now(), LocalDate.now(), LocalDate.now().plusDays(30),
                3, new BigDecimal("4500.00"), BigDecimal.ZERO);
    }

    @Test
    void createComputesTotalFromSubtotalAndVat() {
        TrainProvInvoice invoice = newInvoice();
        assertEquals(new BigDecimal("4500.00"), invoice.getTotal());
        assertEquals("DRAFT", invoice.getStatus());
        assertEquals(3, invoice.getDelegateCount());
    }

    @Test
    void balanceEqualsTotalMinusAmountPaid() {
        TrainProvInvoice invoice = newInvoice();
        assertEquals(new BigDecimal("4500.00"), invoice.balance());
        invoice.recordPayment(new BigDecimal("2000.00"));
        assertEquals(new BigDecimal("2500.00"), invoice.balance());
    }

    @Test
    void recordPaymentMovesToPartialThenPaid() {
        TrainProvInvoice invoice = newInvoice();
        invoice.recordPayment(new BigDecimal("2000.00"));
        assertEquals("PARTIAL", invoice.getStatus());
        invoice.recordPayment(new BigDecimal("2500.00"));
        assertEquals("PAID", invoice.getStatus());
        assertNotNull(invoice.getPaidAt());
    }

    @Test
    void recordPaymentRejectsNonPositiveAmount() {
        TrainProvInvoice invoice = newInvoice();
        assertThrows(IllegalArgumentException.class, () -> invoice.recordPayment(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> invoice.recordPayment(new BigDecimal("-1")));
    }

    @Test
    void markSentRequiresDraft() {
        TrainProvInvoice invoice = newInvoice();
        invoice.markSent();
        assertEquals("SENT", invoice.getStatus());
        assertThrows(IllegalStateException.class, invoice::markSent);
    }
}
