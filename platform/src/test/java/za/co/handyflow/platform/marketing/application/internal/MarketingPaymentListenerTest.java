package za.co.handyflow.platform.marketing.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.invoicing.ReceiptRecordedEvent;
import za.co.handyflow.platform.invoicing.RefundIssuedEvent;
import za.co.handyflow.platform.marketing.MarketingAttributionFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MarketingPaymentListenerTest {

    CrmFacade crm = mock(CrmFacade.class);
    MarketingAttributionFacade attribution = mock(MarketingAttributionFacade.class);
    MarketingPaymentListener listener = new MarketingPaymentListener(crm, attribution);
    TenantId tenant = TenantId.of(UUID.randomUUID());

    @Test void cents_rounds_half_up_and_handles_null() {
        assertEquals(12350L, MarketingPaymentListener.cents(new BigDecimal("123.50")));
        assertEquals(1L, MarketingPaymentListener.cents(new BigDecimal("0.005")));
        assertEquals(0L, MarketingPaymentListener.cents(null));
    }

    @Test void a_receipt_becomes_a_payment_keyed_by_receipt_number() {
        UUID cust = UUID.randomUUID();
        CustomerSummary c = mock(CustomerSummary.class);
        when(c.email()).thenReturn("a@x.test");
        when(crm.findCustomerById(tenant, cust)).thenReturn(Optional.of(c));
        listener.on(new ReceiptRecordedEvent(tenant, UUID.randomUUID(), "RC-1", cust, new BigDecimal("10.00"), null,
                LocalDate.of(2026, 11, 19), null, "INV-1", Instant.now()));
        verify(attribution).recordPayment(eq(tenant), eq("a@x.test"), eq(1000L), eq("ZAR"), eq("receipt:RC-1"), any(), anyString());
    }

    @Test void a_customer_without_an_email_is_ignored() {
        UUID cust = UUID.randomUUID();
        when(crm.findCustomerById(tenant, cust)).thenReturn(Optional.empty());
        listener.on(new ReceiptRecordedEvent(tenant, UUID.randomUUID(), "RC-2", cust, BigDecimal.TEN, null,
                LocalDate.now(), null, null, Instant.now()));
        verifyNoInteractions(attribution);
    }

    @Test void a_refund_points_back_at_the_original_receipt() {
        listener.on(new RefundIssuedEvent(tenant, UUID.randomUUID(), "RF-1", "RC-1", UUID.randomUUID(), new BigDecimal("4.00"), null,
                LocalDate.of(2026, 11, 19), null, Instant.now()));
        verify(attribution).recordRefund(eq(tenant), eq("receipt:RC-1"), eq("refund:RF-1"), eq(400L), any());
    }
}
