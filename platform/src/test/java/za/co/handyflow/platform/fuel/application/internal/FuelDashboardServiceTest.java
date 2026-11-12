package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.MarginResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelDashboardServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());

    @Mock FuelTankRepository tanks;
    @Mock FuelReservationRepository reservations;
    @Mock FuelSalesOrderRepository orders;
    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelCustomerAccountRepository accounts;
    @Mock FuelComplianceItemRepository compliance;
    @Mock FuelPurchaseOrderRepository purchaseOrders;
    @Mock CrmFacade crm;
    @InjectMocks FuelDashboardService service;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private FuelDelivery delivered(UUID customer, String litres, String price, String cost) {
        FuelDelivery d = mock(FuelDelivery.class);
        when(d.getCustomerId()).thenReturn(customer);
        when(d.getLitresDelivered()).thenReturn(bd(litres));
        when(d.getPricePerLitre()).thenReturn(bd(price));
        when(d.getTotalAmount()).thenReturn(bd(litres).multiply(bd(price)));
        when(d.getCostPerLitreAtSale()).thenReturn(cost == null ? null : bd(cost));
        return d;
    }

    @Test @DisplayName("margin = revenue less cost at sale, per customer; deliveries with no cost are counted, not guessed")
    void marginByCustomer() {
        UUID a = UUID.randomUUID();
        var built1 = List.of(
                delivered(a, "1000", "22.00", "20.00"),
                delivered(a, "500", "22.00", null));
        when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(built1);
        when(crm.findCustomerById(any(), any())).thenReturn(Optional.empty());
        MarginResponse m = service.margin(T, 30);
        assertEquals(1, m.customers().size());
        assertEquals(0, bd("2000.00").compareTo(m.margin()));
        assertEquals(0, bd("33000.00").compareTo(m.revenue()));
        assertEquals(1, m.deliveriesWithoutCost());
        assertEquals(0, bd("2.0000").compareTo(m.customers().get(0).marginPerLitre()));
    }

    @Test @DisplayName("reversed deliveries are left out of margin")
    void reversedIgnored() {
        UUID a = UUID.randomUUID();
        FuelDelivery d = delivered(a, "1000", "22.00", "20.00");
        when(d.getReversedAt()).thenReturn(Instant.now());
        when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(List.of(d));
        assertTrue(service.margin(T, 30).customers().isEmpty());
    }

    @Test @DisplayName("customers are ranked by margin, highest first")
    void ranked() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var built2 = List.of(
                delivered(a, "100", "22.00", "21.00"), delivered(b, "100", "25.00", "20.00"));
        when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(built2);
        when(crm.findCustomerById(any(), any())).thenReturn(Optional.empty());
        assertEquals(b, service.margin(T, 30).customers().get(0).customerId());
    }
}
