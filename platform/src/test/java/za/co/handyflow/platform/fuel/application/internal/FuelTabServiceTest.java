package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.bookings.application.BookingsFacade;
import za.co.handyflow.platform.bookings.application.BookingsFacade.ServiceInfo;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopOffering;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTab;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTabLine;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopOfferingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabLineRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabRepository;
import za.co.handyflow.platform.fuel.dto.TabDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Written, not compiled or run. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelTabServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();
    @Mock FuelTruckStopTabRepository tabs;
    @Mock FuelTruckStopTabLineRepository lines;
    @Mock FuelTruckStopOfferingRepository offerings;
    @Mock FuelCustomerAccountRepository accounts;
    @Mock BookingsFacade bookings;
    @InjectMocks FuelTabService service;

    final UUID serviceId = UUID.randomUUID();
    final FuelTruckStopOffering shower = FuelTruckStopOffering.create(T, serviceId, "SHOWER", null, null);
    final List<FuelTruckStopTabLine> stored = new ArrayList<>();

    private FuelTruckStopTab openTab(UUID accountId) {
        FuelTruckStopTab t = FuelTruckStopTab.open(T, "ca 123 gp", "Sipho", accountId, USER);
        when(tabs.findOwned(T, t.getId())).thenReturn(Optional.of(t));
        when(tabs.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tabs.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(lines.findForTab(eq(T), eq(t.getId()))).thenAnswer(i -> new ArrayList<>(stored));
        when(lines.save(any())).thenAnswer(i -> { stored.add(i.getArgument(0)); return i.getArgument(0); });
        when(offerings.findOwned(T, shower.getId())).thenReturn(Optional.of(shower));
        when(bookings.services(T)).thenReturn(List.of(new ServiceInfo(serviceId, "Shower", 20, new BigDecimal("45.00"), true)));
        return t;
    }

    private void addShower(FuelTruckStopTab t, String qty) { service.addLine(T, t.getId(), new AddLineRequest(shower.getId(), new BigDecimal(qty)), USER); }

    @Test @DisplayName("a line takes the service's Bookings price and name, never a typed price, and the total adds up")
    void priceComesFromBookings() {
        FuelTruckStopTab t = openTab(null);
        addShower(t, "2");
        ArgumentCaptor<FuelTruckStopTabLine> saved = ArgumentCaptor.forClass(FuelTruckStopTabLine.class);
        verify(lines).save(saved.capture());
        assertEquals(0, new BigDecimal("45.00").compareTo(saved.getValue().getUnitPrice()));
        assertEquals("Shower", saved.getValue().getDescription());
        addShower(t, "1.5");
        assertEquals(0, new BigDecimal("90.00").compareTo(stored.get(0).amount()));
        assertEquals(0, new BigDecimal("67.50").compareTo(stored.get(1).amount()));
    }

    @Test @DisplayName("a service with no Bookings price, an inactive one, or a switched-off offering cannot be added")
    void refusesUnpricedOrInactive() {
        FuelTruckStopTab t = openTab(null);
        when(bookings.services(T)).thenReturn(List.of(new ServiceInfo(serviceId, "Shower", 20, null, true)));
        assertThrows(IllegalArgumentException.class, () -> addShower(t, "1"));
        when(bookings.services(T)).thenReturn(List.of(new ServiceInfo(serviceId, "Shower", 20, new BigDecimal("45"), false)));
        assertThrows(IllegalStateException.class, () -> addShower(t, "1"));
        when(bookings.services(T)).thenReturn(List.of(new ServiceInfo(serviceId, "Shower", 20, new BigDecimal("45"), true)));
        shower.setActive(false);
        assertThrows(IllegalStateException.class, () -> addShower(t, "1"));
        assertTrue(stored.isEmpty());
    }

    @Test @DisplayName("a tab with no lines cannot be settled; with lines it settles at the till as cash")
    void settleAtTill() {
        FuelTruckStopTab t = openTab(null);
        assertThrows(IllegalStateException.class, () -> service.settle(T, t.getId(), "CASH", USER));
        addShower(t, "1");
        TabView v = service.settle(T, t.getId(), "cash", USER);
        assertEquals("SETTLED", v.status());
        assertEquals("CASH", v.paymentMethod());
        assertThrows(IllegalStateException.class, () -> addShower(t, "1"));
    }

    @Test @DisplayName("a tab charged to an account is not settled at the till, and a till tab cannot be charged")
    void accountVersusTill() {
        UUID acct = UUID.randomUUID();
        FuelCustomerAccount a = mock(FuelCustomerAccount.class);
        when(a.isCreditHold()).thenReturn(false);
        when(accounts.findActiveById(T, acct)).thenReturn(Optional.of(a));
        FuelTruckStopTab onAccount = openTab(acct);
        addShower(onAccount, "1");
        assertThrows(IllegalStateException.class, () -> service.settle(T, onAccount.getId(), "CASH", USER));
        FuelTruckStopTab till = openTab(null);
        assertThrows(IllegalStateException.class, () -> service.chargeToAccount(T, till.getId(), USER));
    }

    @Test @DisplayName("charging to an account queues the draft invoice and refuses an account on credit hold")
    void chargeQueuesInvoice() {
        UUID acct = UUID.randomUUID();
        FuelCustomerAccount a = mock(FuelCustomerAccount.class);
        when(a.isCreditHold()).thenReturn(false);
        when(accounts.findActiveById(T, acct)).thenReturn(Optional.of(a));
        FuelTruckStopTab t = openTab(acct);
        addShower(t, "1");
        TabView v = service.chargeToAccount(T, t.getId(), USER);
        assertEquals("SETTLED", v.status());
        assertEquals("ACCOUNT", v.paymentMethod());
        verify(tabs).markPending(T, t.getId());

        when(a.isCreditHold()).thenReturn(true);
        FuelTruckStopTab held = openTab(acct);
        addShower(held, "1");
        assertThrows(IllegalStateException.class, () -> service.chargeToAccount(T, held.getId(), USER));
    }

    @Test @DisplayName("an open tab can be voided with a reason; a settled tab cannot")
    void voiding() {
        FuelTruckStopTab t = openTab(null);
        assertThrows(IllegalArgumentException.class, () -> service.voidTab(T, t.getId(), "no", USER));
        assertEquals("VOID", service.voidTab(T, t.getId(), "Driver left without paying", USER).status());
        FuelTruckStopTab done = openTab(null);
        addShower(done, "1");
        service.settle(T, done.getId(), "CARD", USER);
        assertThrows(IllegalStateException.class, () -> service.voidTab(T, done.getId(), "Changed mind", USER));
    }

    @Test @DisplayName("a quantity of zero, a negative one and an absurd one are refused")
    void quantityChecks() {
        FuelTruckStopTab t = openTab(null);
        assertThrows(IllegalArgumentException.class, () -> addShower(t, "0"));
        assertThrows(IllegalArgumentException.class, () -> addShower(t, "-1"));
        assertThrows(IllegalArgumentException.class, () -> addShower(t, "1000"));
    }

    @Test @DisplayName("a blank registration is refused and the registration is stored upper case")
    void registration() {
        assertThrows(IllegalArgumentException.class, () -> FuelTruckStopTab.open(T, "  ", null, null, null));
        assertEquals("CA 123 GP", FuelTruckStopTab.open(T, " ca 123 gp ", null, null, null).getVehicleRegistration());
    }
}
