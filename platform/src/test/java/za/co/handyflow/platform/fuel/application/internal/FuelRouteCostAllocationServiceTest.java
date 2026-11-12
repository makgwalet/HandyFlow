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
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerSite;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelRoute;
import za.co.handyflow.platform.fuel.domain.model.FuelRouteCostEntry;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerSiteRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelRouteCostEntryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelRouteRepository;
import za.co.handyflow.platform.fuel.dto.RouteCostAllocationDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelRouteCostAllocationServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID BY = UUID.randomUUID();
    static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelRouteRepository routes;
    @Mock FuelRouteCostEntryRepository entries;
    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelCustomerSiteRepository sites;
    @Mock CrmFacade crm;
    @InjectMocks FuelRouteCostAllocationService service;

    FuelRoute route;
    final UUID tankId = UUID.randomUUID(), acme = UUID.randomUUID(), bolt = UUID.randomUUID();
    final List<FuelRouteCostEntry> actuals = new ArrayList<>();

    @BeforeEach
    void setUp() {
        route = FuelRoute.create(T, DAY, "Run", UUID.randomUUID(), UUID.randomUUID(), BY);
        when(routes.findBetween(eq(T), any(), any())).thenReturn(List.of(route));
        when(entries.findActiveByRoutes(eq(T), any())).thenAnswer(i -> actuals.stream().filter(FuelRouteCostEntry::isActive).toList());
        CustomerSummary a = new CustomerSummary(acme, "Acme", null, null, null, null, null, null);
        CustomerSummary b = new CustomerSummary(bolt, "Bolt", null, null, null, null, null, null);
        when(crm.findCustomerById(T, acme)).thenReturn(Optional.of(a));
        when(crm.findCustomerById(T, bolt)).thenReturn(Optional.of(b));
    }

    private FuelDelivery delivered(UUID customer, String litres, UUID siteId) {
        FuelDelivery d = FuelDelivery.create(T, tankId, customer, Map.of(), "DIESEL", bd(litres), bd("22"), Instant.now(), null, null);
        if (siteId != null) d.assignSite(siteId);
        d.joinRoute(route.getId(), 1);
        d.dispatch("D", "V");
        d.complete(bd(litres), "R", null, null, null, false, null, null);
        return d;
    }

    private void withStops(FuelDelivery... ds) { when(deliveries.findByRoutes(eq(T), any())).thenReturn(List.of(ds)); }

    private void cost(String category, String amount) { actuals.add(FuelRouteCostEntry.create(T, route.getId(), category, bd(amount), null, null, "x", BY)); }

    private CustomerShare share(AllocationReport r, UUID customer) { return r.customers().stream().filter(c -> customer.equals(c.customerId())).findFirst().orElseThrow(); }

    @Test @DisplayName("by litres, a route's whole actual cost is shared in proportion to litres delivered")
    void byLitres() {
        withStops(delivered(acme, "3000", null), delivered(bolt, "1000", null));
        cost("VEHICLE", "800"); cost("TOLLS", "200");
        AllocationReport r = service.report(T, DAY, DAY, Method.LITRES);
        assertEquals(bd("750.00"), share(r, acme).allocated());
        assertEquals(bd("250.00"), share(r, bolt).allocated());
        assertEquals(bd("1000.00"), r.allocatedTotal());
        assertEquals(bd("0.2500"), share(r, acme).costPerLitre());
        assertEquals(Method.LITRES, r.method());
    }

    @Test @DisplayName("no method given means litres")
    void defaultMethod() {
        withStops(delivered(acme, "1000", null));
        cost("DRIVER", "100");
        assertEquals(Method.LITRES, service.report(T, DAY, DAY, null).method());
    }

    @Test @DisplayName("by stops, every delivered stop carries the same share")
    void byStops() {
        withStops(delivered(acme, "9000", null), delivered(bolt, "100", null));
        cost("VEHICLE", "600");
        AllocationReport r = service.report(T, DAY, DAY, Method.STOPS);
        assertEquals(bd("300.00"), share(r, acme).allocated());
        assertEquals(bd("300.00"), share(r, bolt).allocated());
    }

    @Test @DisplayName("by distance, a farther site carries more of the cost")
    void byDistance() {
        UUID near = UUID.randomUUID(), far = UUID.randomUUID();
        FuelCustomerSite sNear = mock(FuelCustomerSite.class); when(sNear.getId()).thenReturn(near); when(sNear.getDistanceKm()).thenReturn(bd("10")); when(sNear.getTenantId()).thenReturn(T);
        FuelCustomerSite sFar = mock(FuelCustomerSite.class); when(sFar.getId()).thenReturn(far); when(sFar.getDistanceKm()).thenReturn(bd("30")); when(sFar.getTenantId()).thenReturn(T);
        List<FuelCustomerSite> found = List.of(sNear, sFar);
        when(sites.findAllById(any())).thenReturn(found);
        withStops(delivered(acme, "1000", near), delivered(bolt, "1000", far));
        cost("VEHICLE", "400");
        AllocationReport r = service.report(T, DAY, DAY, Method.DISTANCE);
        assertEquals(bd("100.00"), share(r, acme).allocated());
        assertEquals(bd("300.00"), share(r, bolt).allocated());
        assertEquals(0, r.fallbackRoutes());
    }

    @Test @DisplayName("when the method has no usable weights the route is split equally and counted as a fallback")
    void fallback() {
        withStops(delivered(acme, "1000", null), delivered(bolt, "1000", null)); // no site distances
        cost("VEHICLE", "500");
        AllocationReport r = service.report(T, DAY, DAY, Method.DISTANCE);
        assertEquals(bd("250.00"), share(r, acme).allocated());
        assertEquals(bd("250.00"), share(r, bolt).allocated());
        assertEquals(1, r.fallbackRoutes());
    }

    @Test @DisplayName("shares always add up to the recorded total to the cent")
    void roundingKeepsTheTotal() {
        UUID c3 = UUID.randomUUID();
        withStops(delivered(acme, "1000", null), delivered(bolt, "1000", null), delivered(c3, "1000", null));
        cost("VEHICLE", "100.00");
        AllocationReport r = service.report(T, DAY, DAY, Method.STOPS);
        BigDecimal sum = r.customers().stream().map(CustomerShare::allocated).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(bd("100.00"), sum);
    }

    @Test @DisplayName("a route with no recorded actual costs is skipped and counted, never filled with an estimate")
    void skippedWithoutActuals() {
        withStops(delivered(acme, "1000", null));
        AllocationReport r = service.report(T, DAY, DAY, Method.LITRES);
        assertEquals(0, r.routesAllocated());
        assertEquals(1, r.routesSkipped());
        assertTrue(r.customers().isEmpty());
        assertEquals(0, r.allocatedTotal().signum());
    }

    @Test @DisplayName("voided entries are not allocated and undelivered stops carry no cost")
    void undeliveredAndVoided() {
        FuelDelivery notDone = FuelDelivery.create(T, tankId, bolt, Map.of(), "DIESEL", bd("1000"), bd("22"), Instant.now(), null, null);
        notDone.joinRoute(route.getId(), 2);
        withStops(delivered(acme, "1000", null), notDone);
        cost("VEHICLE", "300");
        FuelRouteCostEntry gone = FuelRouteCostEntry.create(T, route.getId(), "OTHER", bd("999"), null, null, "x", BY);
        gone.voidEntry("entered twice", BY);
        actuals.add(gone);
        AllocationReport r = service.report(T, DAY, DAY, Method.LITRES);
        assertEquals(1, r.customers().size());
        assertEquals(bd("300.00"), share(r, acme).allocated());
    }

    @Test @DisplayName("one customer on two stops of a route is one route, two stops")
    void sameCustomerTwoStops() {
        withStops(delivered(acme, "1000", null), delivered(acme, "1000", null));
        cost("VEHICLE", "200");
        CustomerShare s = share(service.report(T, DAY, DAY, Method.LITRES), acme);
        assertEquals(1, s.routes());
        assertEquals(2, s.stops());
        assertEquals(bd("200.00"), s.allocated());
    }

    @Test @DisplayName("a bad date range is refused")
    void badRange() {
        assertThrows(IllegalArgumentException.class, () -> service.report(T, DAY.plusDays(1), DAY, Method.LITRES));
        assertThrows(IllegalArgumentException.class, () -> service.report(T, DAY.minusDays(400), DAY, Method.LITRES));
    }

    @Test @DisplayName("split gives the rounding difference to the largest share")
    void splitHelper() {
        List<BigDecimal> shares = FuelRouteCostAllocationService.split(bd("10.00"), List.of(bd("1"), bd("1"), bd("2")));
        assertEquals(bd("10.00"), shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(bd("5.00"), shares.get(2));
    }
}
