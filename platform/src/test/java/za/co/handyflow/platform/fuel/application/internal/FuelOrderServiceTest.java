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
import za.co.handyflow.platform.fuel.domain.model.FuelSalesOrder;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelOrderServiceTest {

    @Mock FuelSalesOrderRepository orders;
    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelCustomerAccountRepository accounts;
    @Mock FuelCustomerSiteRepository sites;
    @Mock FuelTankRepository tanks;
    @Mock FuelReservationRepository reservations;
    @Mock FuelStockService stock;
    @Mock FuelService fuelService;
    @Mock OrderNumberGenerator numbers;
    @Mock InvoicingFacade invoicing;
    @Mock CrmFacade crm;
    @InjectMocks FuelOrderService service;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }
    final UUID customer = UUID.randomUUID();
    FuelCustomerAccount account;
    FuelTank tank;
    FuelSalesOrder order;

    @BeforeEach
    void setUp() {
        account = FuelCustomerAccount.create(T, customer, "MINING_COMPANY", bd("1000000"), 30, null, null);
        tank = FuelTank.create(T, "Diesel 1", "DIESEL", bd("50000"), "Yard", null);
        order = FuelSalesOrder.create(T, "SO-2026-00001", account.getId(), customer, null, "DIESEL", bd("30000"), bd("22.50"), null, null, null, null, null);
        when(orders.findActiveByIdForUpdate(eq(T), any())).thenReturn(Optional.of(order));
        when(orders.findActiveById(eq(T), any())).thenReturn(Optional.of(order));
        when(accounts.findActiveById(eq(T), any())).thenReturn(Optional.of(account));
        when(tanks.findActiveById(eq(T), any())).thenReturn(Optional.of(tank));
        when(deliveries.sumDeliveredForOrder(any(), any())).thenReturn(BigDecimal.ZERO);
        when(deliveries.sumPendingForOrder(any(), any())).thenReturn(BigDecimal.ZERO);
        when(deliveries.sumUninvoicedValue(any(), any())).thenReturn(BigDecimal.ZERO);
        when(orders.findOpenForCustomer(any(), any())).thenReturn(List.of());
        when(invoicing.findOutstandingInvoices(any())).thenReturn(List.of());
        when(orders.save(any())).thenAnswer(i -> i.getArgument(0));
        when(crm.findCustomerById(any(), any())).thenReturn(Optional.empty());
    }

    private ConfirmOrderRequest confirmReq(String override) { return new ConfirmOrderRequest(tank.getId(), override); }

    @Test @DisplayName("confirming within the limit reserves exactly the ordered litres against the order")
    void confirmReserves() {
        service.confirm(T, order.getId(), confirmReq(null), false, UUID.randomUUID());
        assertEquals("CONFIRMED", order.getStatus());
        ArgumentCaptor<ReservationRequest> c = ArgumentCaptor.forClass(ReservationRequest.class);
        verify(stock).reserve(eq(T), c.capture(), any());
        assertEquals(0, bd("30000").compareTo(c.getValue().litres()));
        assertEquals("ORDER", c.getValue().referenceType());
        assertEquals(order.getId(), c.getValue().referenceId());
    }

    @Test @DisplayName("credit exposure adds unpaid invoices, uninvoiced deliveries, other open orders and this order")
    void creditExposure() {
        when(invoicing.findOutstandingInvoices(any())).thenReturn(List.of(
                new InvoicingFacade.OutstandingInvoiceSummary(UUID.randomUUID(), "INV-1", customer, null, LocalDate.now(), bd("1000"), bd("200"), bd("100")),
                new InvoicingFacade.OutstandingInvoiceSummary(UUID.randomUUID(), "INV-2", UUID.randomUUID(), null, LocalDate.now(), bd("9999"), null, null)));
        when(deliveries.sumUninvoicedValue(T, customer)).thenReturn(bd("5000"));
        FuelSalesOrder other = FuelSalesOrder.create(T, "SO-2", account.getId(), customer, null, "DIESEL", bd("1000"), bd("20"), null, null, null, null, null);
        when(orders.findOpenForCustomer(T, customer)).thenReturn(List.of(other));
        when(deliveries.sumDeliveredForOrder(T, other.getId())).thenReturn(bd("400"));
        CreditCheckResponse r = service.creditCheck(T, order.getId());
        assertEquals(0, bd("700").compareTo(r.invoicesOutstanding()));      // 1000 - 200 - 100, other customer ignored
        assertEquals(0, bd("5000").compareTo(r.deliveredNotInvoiced()));
        assertEquals(0, bd("12000").compareTo(r.otherOpenOrders()));        // 600 L x 20
        assertEquals(0, bd("675000.00").compareTo(r.thisOrder()));
        assertEquals(0, bd("692700.00").compareTo(r.exposureAfter()));
        assertTrue(r.withinLimit());
    }

    @Test @DisplayName("over the limit without override permission is refused and nothing is reserved")
    void overLimitRefused() {
        FuelCustomerAccount small = FuelCustomerAccount.create(T, customer, "MINING_COMPANY", bd("100000"), 30, null, null);
        when(accounts.findActiveById(eq(T), any())).thenReturn(Optional.of(small));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.confirm(T, order.getId(), confirmReq("Director approved"), false, UUID.randomUUID()));
        assertTrue(e.getMessage().contains("credit limit"));
        assertEquals("QUOTE", order.getStatus());
        verify(stock, never()).reserve(any(), any(), any());
    }

    @Test @DisplayName("an override needs a reason, then confirms and records it")
    void overrideNeedsReason() {
        FuelCustomerAccount small = FuelCustomerAccount.create(T, customer, "MINING_COMPANY", bd("100000"), 30, null, null);
        when(accounts.findActiveById(eq(T), any())).thenReturn(Optional.of(small));
        assertThrows(IllegalArgumentException.class, () -> service.confirm(T, order.getId(), confirmReq("ok"), true, UUID.randomUUID()));
        service.confirm(T, order.getId(), confirmReq("Prepayment received"), true, UUID.randomUUID());
        assertEquals("CONFIRMED", order.getStatus());
        assertEquals("Prepayment received", order.getCreditOverrideReason());
    }

    @Test @DisplayName("a customer on credit hold cannot be confirmed, even with override")
    void creditHoldRefused() {
        account.placeOnHold("Overdue 60 days");
        assertThrows(IllegalStateException.class, () -> service.confirm(T, order.getId(), confirmReq("Because I say so"), true, UUID.randomUUID()));
        verify(stock, never()).reserve(any(), any(), any());
    }

    @Test @DisplayName("the tank must hold the ordered fuel type")
    void wrongFuelType() {
        FuelTank petrol = FuelTank.create(T, "Petrol 1", "PETROL_95", bd("10000"), "Yard", null);
        when(tanks.findActiveById(eq(T), any())).thenReturn(Optional.of(petrol));
        assertThrows(IllegalArgumentException.class, () -> service.confirm(T, order.getId(), new ConfirmOrderRequest(petrol.getId(), null), false, UUID.randomUUID()));
    }

    @Test @DisplayName("if the tank cannot cover the order, confirm fails with the stock message and the exception propagates (transaction rolls back)")
    void insufficientStock() {
        when(stock.reserve(any(), any(), any())).thenThrow(new IllegalStateException("Only 100 L available to promise"));
        assertThrows(IllegalStateException.class, () -> service.confirm(T, order.getId(), confirmReq(null), false, UUID.randomUUID()));
    }

    @Test @DisplayName("loads cannot exceed what is left on the order, and an unconfirmed order takes no loads")
    void loadLimits() {
        OrderDeliveryRequest big = new OrderDeliveryRequest(bd("1000"), java.time.Instant.now(), null, null, null);
        assertThrows(IllegalStateException.class, () -> service.scheduleLoad(T, order.getId(), big));   // still a quote
        order.confirm(tank.getId(), null, LocalDate.now());
        when(deliveries.sumDeliveredForOrder(T, order.getId())).thenReturn(bd("25000"));
        when(deliveries.sumPendingForOrder(T, order.getId())).thenReturn(bd("4500"));
        assertThrows(IllegalArgumentException.class, () -> service.scheduleLoad(T, order.getId(), big)); // only 500 left
        verify(fuelService, never()).scheduleDelivery(any(), any());
    }

    @Test @DisplayName("cancel is refused while loads are scheduled; otherwise it frees the reservation")
    void cancelReleases() {
        order.confirm(tank.getId(), null, LocalDate.now());
        when(deliveries.sumPendingForOrder(T, order.getId())).thenReturn(bd("1000"));
        assertThrows(IllegalStateException.class, () -> service.cancel(T, order.getId(), new OrderCancelRequest("Customer withdrew")));
        when(deliveries.sumPendingForOrder(T, order.getId())).thenReturn(BigDecimal.ZERO);
        var res = za.co.handyflow.platform.fuel.domain.model.FuelReservation.create(T, tank.getId(), bd("30000"), customer, "ORDER", order.getId(), null, null, null);
        when(reservations.findActiveByReference(T, tank.getId(), "ORDER", order.getId())).thenReturn(List.of(res));
        service.cancel(T, order.getId(), new OrderCancelRequest("Customer withdrew"));
        assertEquals("CANCELLED", order.getStatus());
        assertEquals("RELEASED", res.getStatus());
    }

    @Test @DisplayName("another tenant's order is not found")
    void tenantIsolation() {
        UUID id = UUID.randomUUID();
        TenantId other = TenantId.of(UUID.randomUUID());
        when(orders.findActiveByIdForUpdate(eq(other), any())).thenReturn(Optional.empty());
        when(orders.findActiveById(eq(other), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(other, id));
        assertThrows(ResourceNotFoundException.class, () -> service.confirm(other, id, confirmReq(null), true, null));
        assertThrows(ResourceNotFoundException.class, () -> service.cancel(other, id, new OrderCancelRequest("Not yours")));
    }
}
