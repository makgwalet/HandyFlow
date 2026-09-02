package za.co.handyflow.platform.collectionsagency.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CollAgencyCommissionInvoiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    private CollAgencyCommissionInvoice newInvoice() {
        return CollAgencyCommissionInvoice.create(tenantId, clientId, "CI00001", "Commission on remittance",
                LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("200.00"), BigDecimal.ZERO);
    }

    @Test
    void createComputesTotalAsSubtotalPlusVat() {
        CollAgencyCommissionInvoice inv = CollAgencyCommissionInvoice.create(tenantId, clientId, "CI00001", "desc",
                LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("200.00"), new BigDecimal("30.00"));
        assertEquals(new BigDecimal("230.00"), inv.getTotal());
        assertEquals("DRAFT", inv.getStatus());
    }

    @Test
    void markSentTransitionsFromDraft() {
        CollAgencyCommissionInvoice inv = newInvoice();
        inv.markSent();
        assertEquals("SENT", inv.getStatus());
        assertNotNull(inv.getSentAt());
    }

    @Test
    void markSentRejectedWhenNotDraft() {
        CollAgencyCommissionInvoice inv = newInvoice();
        inv.markSent();
        assertThrows(IllegalStateException.class, inv::markSent);
    }

    @Test
    void recordPaymentPartialSetsPartialStatus() {
        CollAgencyCommissionInvoice inv = newInvoice();
        inv.recordPayment(new BigDecimal("100.00"));
        assertEquals("PARTIAL", inv.getStatus());
        assertEquals(new BigDecimal("100.00"), inv.balance());
    }

    @Test
    void recordPaymentInFullSetsPaidStatus() {
        CollAgencyCommissionInvoice inv = newInvoice();
        inv.recordPayment(new BigDecimal("200.00"));
        assertEquals("PAID", inv.getStatus());
        assertEquals(BigDecimal.ZERO, inv.balance());
        assertNotNull(inv.getPaidAt());
    }
}
