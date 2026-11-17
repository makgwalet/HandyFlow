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
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTab;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTabLine;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabLineRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabRepository;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Written, not compiled or run. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelTabInvoicingServiceTest {

    @Mock FuelTruckStopTabRepository tabs;
    @Mock FuelTruckStopTabLineRepository lines;
    @Mock FuelCustomerAccountRepository accounts;
    @Mock FuelTabInvoicingTx tx;
    @Mock InvoicingFacade invoicing;
    @Mock CrmFacade crm;
    @InjectMocks FuelTabInvoicingService service;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    FuelCustomerAccount account;
    FuelTruckStopTab tab;

    @BeforeEach
    void setUp() {
        account = FuelCustomerAccount.create(T, UUID.randomUUID(), "OTHER", bd("5000"), 30, null, null);
        when(accounts.findActiveById(T, account.getId())).thenReturn(Optional.of(account));
        tab = FuelTruckStopTab.open(T, "GP 1", "Sipho", account.getId(), null);
        tab.chargeToAccount(null);
        when(tabs.findOwned(T, tab.getId())).thenReturn(Optional.of(tab));
        when(lines.findForTab(T, tab.getId())).thenReturn(List.of(
                FuelTruckStopTabLine.of(T, tab.getId(), UUID.randomUUID(), "Shower", bd("2"), bd("45"), null),
                FuelTruckStopTabLine.of(T, tab.getId(), UUID.randomUUID(), "Meal", bd("1"), bd("80"), null)));
        when(invoicing.vatTreatment(any(), any())).thenReturn(new InvoicingFacade.VatTreatment(true, bd("15"), true, LocalDate.of(2020, 1, 1)));
    }

    @Test @DisplayName("one invoice line per tab line at the price the line kept, with the company's VAT rate and the account's terms; then links")
    void happyPath() {
        UUID inv = UUID.randomUUID();
        when(invoicing.createDraftInvoice(any(), any())).thenReturn(new InvoicingFacade.DraftInvoiceResult(inv, "INV-5", bd("170"), bd("25.50"), bd("195.50")));
        service.invoice(T, tab.getId());
        ArgumentCaptor<InvoicingFacade.DraftInvoice> c = ArgumentCaptor.forClass(InvoicingFacade.DraftInvoice.class);
        verify(invoicing).createDraftInvoice(eq(T), c.capture());
        assertEquals(2, c.getValue().lines().size());
        assertEquals(0, bd("45").compareTo(c.getValue().lines().get(0).unitPrice()));
        assertEquals(0, bd("2").compareTo(c.getValue().lines().get(0).quantity()));
        assertEquals(0, bd("15").compareTo(c.getValue().lines().get(1).vatRate()));
        assertTrue(c.getValue().lines().get(0).description().contains("GP 1"));
        assertEquals(LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg")).plusDays(30), c.getValue().dueDate());
        verify(tx).claim(T, tab.getId());
        verify(tx).link(T, tab.getId(), inv, "INV-5");
    }

    @Test @DisplayName("VAT is added once: lines carry ex-VAT prices and the rate, never a price with VAT already in it")
    void noDoubleVat() {
        when(invoicing.createDraftInvoice(any(), any())).thenReturn(new InvoicingFacade.DraftInvoiceResult(UUID.randomUUID(), "INV-6", bd("170"), bd("25.50"), bd("195.50")));
        service.invoice(T, tab.getId());
        ArgumentCaptor<InvoicingFacade.DraftInvoice> c = ArgumentCaptor.forClass(InvoicingFacade.DraftInvoice.class);
        verify(invoicing).createDraftInvoice(eq(T), c.capture());
        BigDecimal exVat = c.getValue().lines().stream().map(l -> l.unitPrice().multiply(l.quantity())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, bd("170").compareTo(exVat));
    }

    @Test @DisplayName("vatToday gives the registered rate, and zero when not registered")
    void vatToday() {
        assertTrue(service.vatToday(T).registered());
        assertEquals(0, bd("15").compareTo(service.vatToday(T).ratePercent()));
        when(invoicing.vatTreatment(any(), any())).thenReturn(new InvoicingFacade.VatTreatment(false, bd("15"), true, LocalDate.of(2020, 1, 1)));
        assertFalse(service.vatToday(T).registered());
        assertEquals(0, BigDecimal.ZERO.compareTo(service.vatToday(T).ratePercent()));
    }

    @Test @DisplayName("when Invoicing fails the tab is marked FAILED for a retry")
    void invoicingFails() {
        when(invoicing.createDraftInvoice(any(), any())).thenThrow(new IllegalStateException("No invoice number series"));
        var e = assertThrows(IllegalStateException.class, () -> service.invoice(T, tab.getId()));
        assertTrue(e.getMessage().contains("No invoice number series"));
        verify(tx).fail(T, tab.getId(), "No invoice number series");
        verify(tx, never()).link(any(), any(), any(), any());
    }

    @Test @DisplayName("an invoice that exists but cannot be linked is marked LINK_FAILED so it is never made twice")
    void linkFails() {
        UUID inv = UUID.randomUUID();
        when(invoicing.createDraftInvoice(any(), any())).thenReturn(new InvoicingFacade.DraftInvoiceResult(inv, "INV-9", bd("1"), bd("0"), bd("1")));
        doThrow(new IllegalStateException("db down")).when(tx).link(any(), any(), any(), any());
        var e = assertThrows(IllegalStateException.class, () -> service.invoice(T, tab.getId()));
        assertTrue(e.getMessage().contains("INV-9"));
        verify(tx).linkFailed(eq(T), eq(tab.getId()), eq(inv), eq("INV-9"), any());
    }

    @Test @DisplayName("a refused claim makes no invoice")
    void claimRefused() {
        doThrow(new IllegalStateException("already invoiced")).when(tx).claim(any(), any());
        assertThrows(IllegalStateException.class, () -> service.invoice(T, tab.getId()));
        verify(invoicing, never()).createDraftInvoice(any(), any());
    }

    @Test @DisplayName("an open tab, or a tab paid at the till, is not invoiced")
    void notInvoiceable() {
        FuelTruckStopTab open = FuelTruckStopTab.open(T, "GP 2", null, account.getId(), null);
        when(tabs.findOwned(T, open.getId())).thenReturn(Optional.of(open));
        assertThrows(IllegalStateException.class, () -> service.invoice(T, open.getId()));
        FuelTruckStopTab till = FuelTruckStopTab.open(T, "GP 3", null, null, null);
        till.settleAtTill("CASH", null);
        when(tabs.findOwned(T, till.getId())).thenReturn(Optional.of(till));
        assertThrows(IllegalStateException.class, () -> service.invoice(T, till.getId()));
        verifyNoInteractions(tx);
    }
}
