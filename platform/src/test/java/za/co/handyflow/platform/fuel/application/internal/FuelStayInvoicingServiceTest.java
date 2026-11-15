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
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStay;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRepository;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelStayInvoicingServiceTest {

    @Mock FuelTruckStopStayRepository stays;
    @Mock FuelCustomerAccountRepository accounts;
    @Mock FuelStayInvoicingTx tx;
    @Mock InvoicingFacade invoicing;
    @Mock CrmFacade crm;
    @InjectMocks FuelStayInvoicingService service;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    FuelCustomerAccount account;
    FuelTruckStopStay stay;

    @BeforeEach
    void setUp() {
        account = FuelCustomerAccount.create(T, UUID.randomUUID(), "OTHER", bd("5000"), 14, null, null);
        when(accounts.findActiveById(T, account.getId())).thenReturn(Optional.of(account));
        stay = FuelTruckStopStay.book(T, "GP 1", "Sipho", null, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 13), bd("450"), null, null);
        stay.chargeTo(account.getId());
        stay.checkIn();
        stay.checkOut(LocalDate.of(2026, 11, 13));
        when(stays.findOwned(T, stay.getId())).thenReturn(Optional.of(stay));
        when(invoicing.vatTreatment(any(), any())).thenReturn(new InvoicingFacade.VatTreatment(true, bd("15"), true, LocalDate.of(2020, 1, 1)));
    }

    @Test @DisplayName("raises one draft invoice line: nights at the booked rate and the company's VAT rate, due on the account's terms; then links")
    void happyPath() {
        UUID inv = UUID.randomUUID();
        when(invoicing.createDraftInvoice(any(), any())).thenReturn(new InvoicingFacade.DraftInvoiceResult(inv, "INV-77", bd("1350"), bd("202.50"), bd("1552.50")));
        service.invoice(T, stay.getId());
        ArgumentCaptor<InvoicingFacade.DraftInvoice> c = ArgumentCaptor.forClass(InvoicingFacade.DraftInvoice.class);
        verify(invoicing).createDraftInvoice(eq(T), c.capture());
        var line = c.getValue().lines().get(0);
        assertEquals(1, c.getValue().lines().size());
        assertEquals(0, bd("3").compareTo(line.quantity()));
        assertEquals(0, bd("450").compareTo(line.unitPrice()));
        assertEquals(0, bd("15").compareTo(line.vatRate()));
        assertEquals(account.getCrmCustomerId(), c.getValue().customerId());
        assertEquals(LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg")).plusDays(14), c.getValue().dueDate());
        verify(tx).claim(T, stay.getId());
        verify(tx).link(T, stay.getId(), inv, "INV-77");
        verify(tx, never()).fail(any(), any(), any());
    }

    @Test @DisplayName("not VAT registered means a zero VAT rate")
    void notRegistered() {
        when(invoicing.vatTreatment(any(), any())).thenReturn(new InvoicingFacade.VatTreatment(false, bd("15"), true, LocalDate.of(2020, 1, 1)));
        when(invoicing.createDraftInvoice(any(), any())).thenReturn(new InvoicingFacade.DraftInvoiceResult(UUID.randomUUID(), "INV-1", bd("1"), bd("0"), bd("1")));
        service.invoice(T, stay.getId());
        ArgumentCaptor<InvoicingFacade.DraftInvoice> c = ArgumentCaptor.forClass(InvoicingFacade.DraftInvoice.class);
        verify(invoicing).createDraftInvoice(any(), c.capture());
        assertEquals(0, BigDecimal.ZERO.compareTo(c.getValue().lines().get(0).vatRate()));
    }

    @Test @DisplayName("when Invoicing fails the stay is marked FAILED for a retry and the error says so")
    void invoicingFails() {
        when(invoicing.createDraftInvoice(any(), any())).thenThrow(new IllegalStateException("No invoice number series"));
        var e = assertThrows(IllegalStateException.class, () -> service.invoice(T, stay.getId()));
        assertTrue(e.getMessage().contains("No invoice number series"));
        verify(tx).fail(T, stay.getId(), "No invoice number series");
        verify(tx, never()).link(any(), any(), any(), any());
    }

    @Test @DisplayName("when the invoice exists but cannot be linked the stay is marked LINK_FAILED so it is never billed twice")
    void linkFails() {
        UUID inv = UUID.randomUUID();
        when(invoicing.createDraftInvoice(any(), any())).thenReturn(new InvoicingFacade.DraftInvoiceResult(inv, "INV-9", bd("1"), bd("0"), bd("1")));
        doThrow(new IllegalStateException("db down")).when(tx).link(any(), any(), any(), any());
        var e = assertThrows(IllegalStateException.class, () -> service.invoice(T, stay.getId()));
        assertTrue(e.getMessage().contains("INV-9"));
        verify(tx).linkFailed(eq(T), eq(stay.getId()), eq(inv), eq("INV-9"), any());
    }

    @Test @DisplayName("a claim that is refused (already invoiced or in progress) creates no invoice")
    void claimRefused() {
        doThrow(new IllegalStateException("already invoiced")).when(tx).claim(any(), any());
        assertThrows(IllegalStateException.class, () -> service.invoice(T, stay.getId()));
        verify(invoicing, never()).createDraftInvoice(any(), any());
    }

    @Test @DisplayName("a stay not yet checked out, or with no account, is not invoiced")
    void notInvoiceable() {
        var open = FuelTruckStopStay.book(T, "GP 2", null, null, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), bd("450"), null, null);
        when(stays.findOwned(T, open.getId())).thenReturn(Optional.of(open));
        assertThrows(IllegalStateException.class, () -> service.invoice(T, open.getId()));
        var gate = FuelTruckStopStay.book(T, "GP 3", null, null, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), bd("450"), null, null);
        gate.checkIn(); gate.checkOut(LocalDate.of(2026, 11, 12));
        when(stays.findOwned(T, gate.getId())).thenReturn(Optional.of(gate));
        assertThrows(IllegalStateException.class, () -> service.invoice(T, gate.getId()));
        verifyNoInteractions(tx);
    }
}
