package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.CreateInvoiceFromDeliveriesRequest;
import za.co.handyflow.platform.fuel.dto.InvoiceCreatedResponse;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelInvoicingServiceTest {

    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelCustomerAccountRepository accounts;
    @Mock FuelInvoicingTx tx;
    @Mock InvoicingFacade invoicing;
    @Mock CrmFacade crm;
    @InjectMocks FuelInvoicingService service;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }
    final UUID customer = UUID.randomUUID();

    private FuelDelivery delivered(UUID cust, String litres) {
        FuelDelivery d = FuelDelivery.create(T, UUID.randomUUID(), cust, Map.of(), "DIESEL", bd(litres), bd("22.50"), Instant.now(), null, null);
        d.complete(bd(litres), "P Botha", null, null, null, false, null, null);
        d.assignReceiptNumber("FDR-2026-0000" + (int) (Math.random() * 9 + 1));
        return d;
    }

    @BeforeEach
    void setUp() {
        when(invoicing.vatTreatment(any(), any())).thenReturn(new InvoicingFacade.VatTreatment(true, bd("15"), true, LocalDate.of(2020, 1, 1)));
        when(accounts.findByCrmCustomerId(any(), any())).thenReturn(Optional.empty());
    }

    @Test @DisplayName("creates one draft invoice with a line per delivery at the delivery price and the tenant VAT rate, then links")
    void happyPath() {
        FuelDelivery a = delivered(customer, "1000"), b = delivered(customer, "2000");
        when(deliveries.findAllByIds(eq(T), any())).thenReturn(new ArrayList<>(List.of(a, b)));
        UUID inv = UUID.randomUUID();
        when(invoicing.createDraftInvoice(any(), any())).thenReturn(new InvoicingFacade.DraftInvoiceResult(inv, "INV-00042", bd("67500"), bd("10125"), bd("77625")));
        InvoiceCreatedResponse r = service.invoice(T, new CreateInvoiceFromDeliveriesRequest(List.of(a.getId(), b.getId())));
        assertEquals("INV-00042", r.invoiceNumber());
        assertEquals(2, r.deliveries());
        ArgumentCaptor<InvoicingFacade.DraftInvoice> c = ArgumentCaptor.forClass(InvoicingFacade.DraftInvoice.class);
        verify(invoicing).createDraftInvoice(eq(T), c.capture());
        assertEquals(2, c.getValue().lines().size());
        assertEquals(0, bd("15").compareTo(c.getValue().lines().get(0).vatRate()));
        assertEquals(0, bd("22.50").compareTo(c.getValue().lines().get(0).unitPrice()));
        verify(tx).claim(eq(T), any());
        verify(tx).link(eq(T), any(), eq(inv), eq("INV-00042"));
        verify(tx, never()).fail(any(), any(), any());
    }

    @Test @DisplayName("not VAT registered means a zero VAT rate")
    void notRegistered() {
        when(invoicing.vatTreatment(any(), any())).thenReturn(new InvoicingFacade.VatTreatment(false, bd("15"), true, LocalDate.of(2020, 1, 1)));
        FuelDelivery a = delivered(customer, "1000");
        when(deliveries.findAllByIds(eq(T), any())).thenReturn(new ArrayList<>(List.of(a)));
        when(invoicing.createDraftInvoice(any(), any())).thenReturn(new InvoicingFacade.DraftInvoiceResult(UUID.randomUUID(), "INV-1", bd("1"), bd("0"), bd("1")));
        service.invoice(T, new CreateInvoiceFromDeliveriesRequest(List.of(a.getId())));
        ArgumentCaptor<InvoicingFacade.DraftInvoice> c = ArgumentCaptor.forClass(InvoicingFacade.DraftInvoice.class);
        verify(invoicing).createDraftInvoice(any(), c.capture());
        assertEquals(0, BigDecimal.ZERO.compareTo(c.getValue().lines().get(0).vatRate()));
    }

    @Test @DisplayName("when Invoicing fails the failure is recorded on the deliveries (they stay in the queue) and nothing is linked")
    void failureStaysVisible() {
        FuelDelivery a = delivered(customer, "1000");
        when(deliveries.findAllByIds(eq(T), any())).thenReturn(new ArrayList<>(List.of(a)));
        when(invoicing.createDraftInvoice(any(), any())).thenThrow(new IllegalArgumentException("That customer was not found."));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.invoice(T, new CreateInvoiceFromDeliveriesRequest(List.of(a.getId()))));
        assertTrue(e.getMessage().contains("stay in the queue"));
        verify(tx).fail(eq(T), any(), eq("That customer was not found."));
        verify(tx, never()).link(any(), any(), any(), any());
    }

    @Test @DisplayName("a refused claim (already invoiced / double click) stops before Invoicing is called")
    void claimRefused() {
        FuelDelivery a = delivered(customer, "1000");
        when(deliveries.findAllByIds(eq(T), any())).thenReturn(new ArrayList<>(List.of(a)));
        doThrow(new IllegalStateException("already invoiced")).when(tx).claim(any(), any());
        assertThrows(IllegalStateException.class, () -> service.invoice(T, new CreateInvoiceFromDeliveriesRequest(List.of(a.getId()))));
        verify(invoicing, never()).createDraftInvoice(any(), any());
    }

    @Test @DisplayName("mixed customers, walk-ins, unfinished and foreign deliveries are refused")
    void guards() {
        FuelDelivery a = delivered(customer, "1000"), other = delivered(UUID.randomUUID(), "1000"), walkIn = delivered(null, "1000");
        when(deliveries.findAllByIds(eq(T), any())).thenReturn(new ArrayList<>(List.of(a, other)));
        assertThrows(IllegalArgumentException.class, () -> service.invoice(T, new CreateInvoiceFromDeliveriesRequest(List.of(a.getId(), other.getId()))));
        when(deliveries.findAllByIds(eq(T), any())).thenReturn(new ArrayList<>(List.of(walkIn)));
        assertThrows(IllegalArgumentException.class, () -> service.invoice(T, new CreateInvoiceFromDeliveriesRequest(List.of(walkIn.getId()))));
        FuelDelivery scheduled = FuelDelivery.create(T, UUID.randomUUID(), customer, Map.of(), "DIESEL", bd("10"), bd("1"), Instant.now(), null, null);
        when(deliveries.findAllByIds(eq(T), any())).thenReturn(new ArrayList<>(List.of(scheduled)));
        assertThrows(IllegalStateException.class, () -> service.invoice(T, new CreateInvoiceFromDeliveriesRequest(List.of(scheduled.getId()))));
        // asked for two, tenant scoping returned one (the other belongs to someone else)
        when(deliveries.findAllByIds(eq(T), any())).thenReturn(new ArrayList<>(List.of(a)));
        assertThrows(za.co.handyflow.platform.shared.ResourceNotFoundException.class,
                () -> service.invoice(T, new CreateInvoiceFromDeliveriesRequest(List.of(a.getId(), UUID.randomUUID()))));
        verify(tx, never()).claim(any(), any());
    }

    @Test @DisplayName("an invoiced delivery cannot be reversed or loaded again (locked)")
    void invoicedLocks() {
        FuelDelivery a = delivered(customer, "1000");
        assertFalse(a.isInvoiceLocked());
        a.markInvoiced(UUID.randomUUID(), "INV-9");
        assertTrue(a.isInvoiceLocked());
        a.markInvoiceFailed("x");
        assertFalse(a.isInvoiceLocked());
    }
}
