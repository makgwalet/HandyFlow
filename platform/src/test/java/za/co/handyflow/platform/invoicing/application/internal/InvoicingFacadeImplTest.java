package za.co.handyflow.platform.invoicing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.SaleLine;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.model.InvoiceLineItem;
import za.co.handyflow.platform.invoicing.domain.model.InvoiceStatus;
import za.co.handyflow.platform.invoicing.domain.repository.CreditNoteRepository;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The read-only sale-line view other modules (Agriculture) use to attribute revenue; nothing here can change a sale. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoicingFacadeImplTest {

    @Mock InvoiceRepository invoiceRepo;
    @Mock CreditNoteRepository creditNoteRepo;
    @Mock CrmFacade crmFacade;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    static final Instant ISSUED = Instant.parse("2026-09-18T08:00:00Z");

    private InvoicingFacadeImpl facade() { return new InvoicingFacadeImpl(invoiceRepo, creditNoteRepo, crmFacade); }

    private static void assertNumber(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private InvoiceLineItem lineItem(UUID id, String description, String qty, String total) {
        InvoiceLineItem li = mock(InvoiceLineItem.class);
        when(li.getId()).thenReturn(id);
        when(li.getDescription()).thenReturn(description);
        when(li.getUnit()).thenReturn("kg");
        when(li.getQuantity()).thenReturn(new BigDecimal(qty));
        when(li.getUnitPrice()).thenReturn(new BigDecimal("42"));
        when(li.getLineTotal()).thenReturn(new BigDecimal(total));
        return li;
    }

    private Invoice invoice(UUID id, String number, InvoiceStatus status, UUID customerId, String walkin, String subtotal, InvoiceLineItem... lines) {
        Invoice inv = mock(Invoice.class);
        when(inv.getId()).thenReturn(id);
        when(inv.getInvoiceNumber()).thenReturn(number);
        when(inv.getStatus()).thenReturn(status);
        when(inv.getCustomerId()).thenReturn(customerId);
        when(inv.getWalkinClientName()).thenReturn(walkin);
        when(inv.getIssuedAt()).thenReturn(ISSUED);
        when(inv.getCurrency()).thenReturn("ZAR");
        when(inv.getSubtotal()).thenReturn(new BigDecimal(subtotal));
        when(inv.getLineItems()).thenReturn(new ArrayList<>(List.of(lines)));
        return inv;
    }

    private void customerNamed(UUID customerId, String name) {
        CustomerSummary c = mock(CustomerSummary.class);
        when(c.name()).thenReturn(name);
        when(crmFacade.findCustomerById(eq(TENANT), eq(customerId))).thenReturn(Optional.of(c));
    }

    @Test
    @DisplayName("only the requested lines come back, with the invoice context, the customer's name and the credit notes")
    void findsOnlyTheRequestedLines() {
        UUID invId = UUID.randomUUID(), customerId = UUID.randomUUID(), wanted = UUID.randomUUID(), other = UUID.randomUUID();
        Invoice inv = invoice(invId, "INV-0042", InvoiceStatus.ISSUED, customerId, null, "2000.00",
                lineItem(wanted, "Broiler chicken", "10", "1000.00"), lineItem(other, "Delivery", "1", "1000.00"));
        when(invoiceRepo.findByLineItemIds(eq(TENANT), any())).thenReturn(List.of(inv));
        when(creditNoteRepo.sumSubtotalByInvoice(eq(TENANT), any())).thenReturn(List.<Object[]>of(new Object[] {invId, new BigDecimal("500.00")}));
        customerNamed(customerId, "ABC Foods");

        List<SaleLine> lines = facade().findSaleLines(TENANT, List.of(wanted));

        assertEquals(1, lines.size());
        SaleLine l = lines.get(0);
        assertEquals(wanted, l.lineItemId());
        assertEquals(invId, l.invoiceId());
        assertEquals("INV-0042", l.invoiceNumber());
        assertEquals("ISSUED", l.invoiceStatus());
        assertEquals("ABC Foods", l.customerName());
        assertEquals("ZAR", l.currency());
        assertEquals(ISSUED, l.issuedAt());
        assertEquals("Broiler chicken", l.description());
        assertEquals("kg", l.unit());
        assertNumber("10", l.quantity());
        assertNumber("1000.00", l.lineTotal());
        assertNumber("2000.00", l.invoiceSubtotal());
        assertNumber("500.00", l.creditedSubtotal());
    }

    @Test
    @DisplayName("an invoice with no credit notes reports zero credited; a walk-in sale uses the walk-in name and asks CRM nothing")
    void walkInAndNoCredit() {
        UUID invId = UUID.randomUUID(), line = UUID.randomUUID();
        when(invoiceRepo.findByLineItemIds(eq(TENANT), any())).thenReturn(List.of(invoice(invId, "INV-7", InvoiceStatus.PAID, null, "Walk-in Co", "300.00", lineItem(line, "Eggs", "5", "300.00"))));
        when(creditNoteRepo.sumSubtotalByInvoice(eq(TENANT), any())).thenReturn(List.<Object[]>of());

        SaleLine l = facade().findSaleLines(TENANT, List.of(line)).get(0);

        assertEquals("Walk-in Co", l.customerName());
        assertNumber("0", l.creditedSubtotal());
        verifyNoInteractions(crmFacade);
    }

    @Test
    @DisplayName("a customer with several invoices is looked up once")
    void customerLookedUpOnce() {
        UUID customerId = UUID.randomUUID(), l1 = UUID.randomUUID(), l2 = UUID.randomUUID();
        when(invoiceRepo.findByLineItemIds(eq(TENANT), any())).thenReturn(List.of(
                invoice(UUID.randomUUID(), "INV-1", InvoiceStatus.PAID, customerId, null, "100.00", lineItem(l1, "A", "1", "100.00")),
                invoice(UUID.randomUUID(), "INV-2", InvoiceStatus.PAID, customerId, null, "200.00", lineItem(l2, "B", "2", "200.00"))));
        when(creditNoteRepo.sumSubtotalByInvoice(eq(TENANT), any())).thenReturn(List.<Object[]>of());
        customerNamed(customerId, "ABC Foods");

        List<SaleLine> lines = facade().findSaleLines(TENANT, List.of(l1, l2));

        assertEquals(2, lines.size());
        verify(crmFacade, times(1)).findCustomerById(eq(TENANT), eq(customerId));
    }

    @Test
    @DisplayName("no ids means no work: nothing is queried")
    void noIds() {
        assertTrue(facade().findSaleLines(TENANT, List.of()).isEmpty());
        assertTrue(facade().findSaleLines(TENANT, null).isEmpty());
        verifyNoInteractions(invoiceRepo);
        verifyNoInteractions(creditNoteRepo);
    }

    @Test
    @DisplayName("a missing line is an empty result, not an error")
    void missingLine() {
        when(invoiceRepo.findByLineItemIds(eq(TENANT), any())).thenReturn(List.of());
        assertTrue(facade().findSaleLine(TENANT, UUID.randomUUID()).isEmpty());
        verifyNoInteractions(creditNoteRepo);
    }

    @Test
    @DisplayName("a search returns every line of the invoices found, for the statuses asked, ignoring unknown status names")
    void searchesBySatus() {
        UUID invId = UUID.randomUUID(), l1 = UUID.randomUUID(), l2 = UUID.randomUUID();
        when(invoiceRepo.findIssuedBetween(eq(TENANT), eq(List.of(InvoiceStatus.ISSUED, InvoiceStatus.PAID)), any(), any(), any()))
                .thenReturn(List.of(invoice(invId, "INV-9", InvoiceStatus.PAID, null, "X", "400.00", lineItem(l1, "A", "1", "100.00"), lineItem(l2, "B", "3", "300.00"))));
        when(creditNoteRepo.sumSubtotalByInvoice(eq(TENANT), any())).thenReturn(List.<Object[]>of());

        List<SaleLine> lines = facade().searchSaleLines(TENANT, ISSUED.minusSeconds(3600), ISSUED.plusSeconds(3600), List.of("ISSUED", "NOT_A_STATUS", "PAID"), 50);

        assertEquals(List.of(l1, l2), lines.stream().map(SaleLine::lineItemId).toList());
    }

    @Test
    @DisplayName("asking for no recognised status finds nothing and queries nothing")
    void noStatuses() {
        assertTrue(facade().searchSaleLines(TENANT, ISSUED, ISSUED, List.of(), 10).isEmpty());
        assertTrue(facade().searchSaleLines(TENANT, ISSUED, ISSUED, List.of("NOPE"), 10).isEmpty());
        assertTrue(facade().searchSaleLines(TENANT, ISSUED, ISSUED, null, 10).isEmpty());
        verifyNoInteractions(invoiceRepo);
    }

    @Test
    @DisplayName("the invoice cap is kept between 1 and 500")
    void capsTheInvoiceCount() {
        when(invoiceRepo.findIssuedBetween(eq(TENANT), any(), any(), any(), any())).thenReturn(List.of());

        facade().searchSaleLines(TENANT, ISSUED, ISSUED, List.of("ISSUED"), 100000);
        facade().searchSaleLines(TENANT, ISSUED, ISSUED, List.of("ISSUED"), -5);

        verify(invoiceRepo).findIssuedBetween(eq(TENANT), any(), any(), any(), eq(org.springframework.data.domain.PageRequest.of(0, 500)));
        verify(invoiceRepo).findIssuedBetween(eq(TENANT), any(), any(), any(), eq(org.springframework.data.domain.PageRequest.of(0, 1)));
    }
}
