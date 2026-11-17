package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStay;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTab;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTabLine;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabLineRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabRepository;
import za.co.handyflow.platform.fuel.dto.AccountStatementDtos.Statement;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Written, not compiled or run. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelAccountStatementServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelCustomerAccountRepository accounts;
    @Mock FuelTruckStopStayRepository stays;
    @Mock FuelTruckStopTabRepository tabs;
    @Mock FuelTruckStopTabLineRepository tabLines;
    @Mock za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository deliveries;
    @Mock za.co.handyflow.platform.invoicing.application.InvoicingFacade invoicing;
    @InjectMocks FuelAccountStatementService service;

    FuelCustomerAccount account;
    final LocalDate from = LocalDate.of(2026, 11, 1), to = LocalDate.of(2026, 11, 30);

    @BeforeEach
    void setUp() {
        account = FuelCustomerAccount.create(T, UUID.randomUUID(), "OTHER", bd("5000"), 30, null, null);
        when(accounts.findActiveById(T, account.getId())).thenReturn(Optional.of(account));
    }

    @Test @DisplayName("lists stays by the day the truck left and tabs by the day they were charged, oldest first, with the total")
    void listsBoth() {
        FuelTruckStopStay stay = FuelTruckStopStay.book(T, "GP 1", "Sipho", null, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 6), bd("450"), null, null);
        stay.chargeTo(account.getId()); stay.checkIn(); stay.checkOut(LocalDate.of(2026, 11, 6));
        FuelTruckStopTab tab = FuelTruckStopTab.open(T, "CA 9", null, account.getId(), null);
        tab.chargeToAccount(null);
        when(stays.findChargedToAccount(T, account.getId(), from, to)).thenReturn(List.of(stay));
        when(tabs.findChargedToAccount(eq(T), eq(account.getId()), any(), any())).thenReturn(List.of(tab));
        when(tabLines.findForTabs(eq(T), any())).thenReturn(List.of(
                FuelTruckStopTabLine.of(T, tab.getId(), UUID.randomUUID(), "Shower", bd("2"), bd("45"), null),
                FuelTruckStopTabLine.of(T, tab.getId(), UUID.randomUUID(), "Meal", bd("1"), bd("80"), null)));
        Statement s = service.statement(T, account.getId(), from, to);
        assertEquals(2, s.lines().size());
        assertEquals("STAY", s.lines().get(0).kind());
        assertEquals(0, bd("1350.00").compareTo(s.lines().get(0).amount()));
        assertEquals("TAB", s.lines().get(1).kind());
        assertEquals(0, bd("170.00").compareTo(s.lines().get(1).amount()));
        assertEquals("2 x Shower, 1 x Meal", s.lines().get(1).description());
        assertEquals(0, bd("1520.00").compareTo(s.total()));
        assertEquals(0, BigDecimal.ZERO.compareTo(s.invoiced()));
        assertEquals(0, bd("1520.00").compareTo(s.notInvoiced()));
    }

    @Test @DisplayName("the tab range is the South African days asked for, from the first midnight to the midnight after the last day")
    void tabRangeIsSastDays() {
        service.statement(T, account.getId(), from, to);
        verify(tabs).findChargedToAccount(T, account.getId(), from.atStartOfDay(SAST).toInstant(), to.plusDays(1).atStartOfDay(SAST).toInstant());
    }

    @Test @DisplayName("a period with nothing charged gives an empty statement with zero totals")
    void empty() {
        Statement s = service.statement(T, account.getId(), from, to);
        assertTrue(s.lines().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(s.total()));
    }

    @Test @DisplayName("a bad period or an unknown account is refused")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> service.statement(T, account.getId(), to, from));
        assertThrows(IllegalArgumentException.class, () -> service.statement(T, account.getId(), null, to));
        assertThrows(IllegalArgumentException.class, () -> service.statement(T, account.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 30)));
        assertThrows(ResourceNotFoundException.class, () -> service.statement(T, UUID.randomUUID(), from, to));
        verifyNoInteractions(stays, tabs);
    }

    private za.co.handyflow.platform.fuel.domain.model.FuelDelivery delivered(UUID customer, String litres, String price) {
        var d = za.co.handyflow.platform.fuel.domain.model.FuelDelivery.create(T, UUID.randomUUID(), customer, java.util.Map.of(), "DIESEL", bd(litres), bd(price), java.time.Instant.now(), null, null);
        d.dispatch("Driver", "GP 77 ZZ"); d.complete(bd(litres), "Receiver", null, null, null, false, null, null);
        return d;
    }

    @Test @DisplayName("delivered fuel for this customer is a line of its own, dated in South African time, and another customer's delivery is left out")
    void listsDeliveries() {
        var mine = delivered(account.getCrmCustomerId(), "5000", "21.50");
        var other = delivered(UUID.randomUUID(), "9000", "21.50");
        when(deliveries.findDeliveredBetween(eq(T), any(), any())).thenReturn(List.of(mine, other));
        Statement s = service.statement(T, account.getId(), from, to);
        assertEquals(1, s.lines().size());
        var l = s.lines().get(0);
        assertEquals("DELIVERY", l.kind());
        assertEquals(0, bd("107500.00").compareTo(l.amount()));
        assertEquals("GP 77 ZZ", l.vehicleRegistration());
        assertTrue(l.description().contains("5000 L DIESEL delivered"));
        assertEquals(java.time.LocalDate.now(SAST), l.date());
    }

    @Test @DisplayName("the division filter narrows the statement: Distribution has no stays or tabs, Truck Stop has no deliveries, anything else is refused")
    void divisionFilter() {
        when(deliveries.findDeliveredBetween(eq(T), any(), any())).thenReturn(List.of(delivered(account.getCrmCustomerId(), "100", "20")));
        FuelTruckStopStay stay = FuelTruckStopStay.book(T, "GP 1", "Sipho", null, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 6), bd("450"), null, null);
        stay.chargeTo(account.getId()); stay.checkIn(); stay.checkOut(LocalDate.of(2026, 11, 6));
        when(stays.findChargedToAccount(T, account.getId(), from, to)).thenReturn(List.of(stay));
        assertEquals(List.of("DELIVERY"), service.statement(T, account.getId(), from, to, "distribution").lines().stream().map(x -> x.kind()).toList());
        assertEquals(List.of("STAY"), service.statement(T, account.getId(), from, to, "TRUCK_STOP").lines().stream().map(x -> x.kind()).toList());
        assertEquals(2, service.statement(T, account.getId(), from, to, null).lines().size());
        assertThrows(IllegalArgumentException.class, () -> service.statement(T, account.getId(), from, to, "RETAIL_FORECOURT"));
    }

    @Test @DisplayName("each invoice Fuel raised shows what Invoicing says: total, paid, credited and what is still owed")
    void invoiceStanding() {
        var d = delivered(account.getCrmCustomerId(), "1000", "20");
        UUID invoice = UUID.randomUUID();
        d.markInvoiced(invoice, "INV-00042");
        when(deliveries.findDeliveredBetween(eq(T), any(), any())).thenReturn(List.of(d));
        when(invoicing.findInvoiceSummaries(eq(T), any())).thenReturn(List.of(new za.co.handyflow.platform.invoicing.application.InvoicingFacade.InvoiceSummary(
                invoice, "INV-00042", "PARTIALLY_PAID", java.time.Instant.now(), bd("23000.00"), bd("10000.00"), bd("3000.00"))));
        Statement s = service.statement(T, account.getId(), from, to);
        assertTrue(s.settlementKnown());
        assertEquals(1, s.invoices().size());
        assertEquals(0, bd("10000.00").compareTo(s.paid()));
        assertEquals(0, bd("3000.00").compareTo(s.credited()));
        assertEquals(0, bd("10000.00").compareTo(s.outstanding()));
        assertEquals(0, bd("20000.00").compareTo(s.invoiced()));
        assertEquals(0, bd("0").compareTo(s.notInvoiced()));
    }

    @Test @DisplayName("if Invoicing cannot be read the lines are still returned and the statement says the settlement is not known")
    void settlementUnavailable() {
        var d = delivered(account.getCrmCustomerId(), "1000", "20");
        d.markInvoiced(UUID.randomUUID(), "INV-1");
        when(deliveries.findDeliveredBetween(eq(T), any(), any())).thenReturn(List.of(d));
        when(invoicing.findInvoiceSummaries(eq(T), any())).thenThrow(new RuntimeException("down"));
        Statement s = service.statement(T, account.getId(), from, to);
        assertEquals(1, s.lines().size());
        assertFalse(s.settlementKnown());
        assertTrue(s.invoices().isEmpty());
    }

    @Test @DisplayName("no invoices means Invoicing is not asked at all")
    void noInvoicesNoCall() {
        service.statement(T, account.getId(), from, to);
        verify(invoicing, never()).findInvoiceSummaries(any(), any());
    }
}
