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
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelRoute;
import za.co.handyflow.platform.fuel.domain.model.FuelTanker;
import za.co.handyflow.platform.fuel.domain.model.FuelTankerCompartment;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelRouteRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankerCompartmentRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankerRepository;
import za.co.handyflow.platform.fuel.dto.AllocationRequest;
import za.co.handyflow.platform.fuel.dto.RouteDtos.*;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelRouteServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    static final LocalDate DAY = LocalDate.of(2026, 11, 13);
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelRouteRepository routes;
    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelTankerRepository tankers;
    @Mock FuelTankerCompartmentRepository compartments;
    @Mock FuelTransportService transport;
    @Mock FuelComplianceService compliance;
    @Mock HrFacade hr;
    @Mock CrmFacade crm;
    @InjectMocks FuelRouteService service;

    FuelTanker tanker; EmployeeSummary driver; FuelRoute route;
    final List<FuelDelivery> onRoute = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tanker = FuelTanker.create(T, "GP 12 AB", null);
        driver = new EmployeeSummary(UUID.randomUUID(), "E1", "Moses", "Sithole", "Moses Sithole", null, null, "Driver", null, "ACTIVE");
        route = FuelRoute.create(T, DAY, "Morning run", tanker.getId(), driver.id(), UUID.randomUUID());
        when(tankers.findActiveById(eq(T), eq(tanker.getId()))).thenReturn(Optional.of(tanker));
        when(compartments.findByTanker(T, tanker.getId())).thenReturn(List.of(FuelTankerCompartment.create(T, tanker.getId(), 1, bd("16000"))));
        when(hr.findEmployeeById(T, driver.id())).thenReturn(Optional.of(driver));
        when(routes.findOne(eq(T), eq(route.getId()))).thenReturn(Optional.of(route));
        when(routes.findOneForUpdate(eq(T), eq(route.getId()))).thenReturn(Optional.of(route));
        when(routes.save(any())).thenAnswer(i -> i.getArgument(0));
        when(deliveries.findByRoute(eq(T), eq(route.getId()))).thenAnswer(i -> new ArrayList<>(onRoute));
    }

    private FuelDelivery load(int hourSast, String litres) {
        Instant at = DAY.atTime(hourSast, 0).atZone(SAST).toInstant();
        FuelDelivery d = FuelDelivery.create(T, UUID.randomUUID(), UUID.randomUUID(), Map.of(), "DIESEL", bd(litres), bd("22"), at, null, null);
        when(deliveries.findActiveByIdForUpdate(eq(T), eq(d.getId()))).thenReturn(Optional.of(d));
        return d;
    }

    private FuelDelivery stop(int seq, int hour, String litres) {
        FuelDelivery d = load(hour, litres);
        d.joinRoute(route.getId(), seq);
        onRoute.add(d);
        return d;
    }

    private AddStopRequest add(FuelDelivery d) {
        return new AddStopRequest(d.getId(), List.of(new AllocationRequest.CompartmentLoad(UUID.randomUUID(), d.getLitresOrdered())));
    }

    @Test @DisplayName("creating a route checks the tanker and driver are compliant and refuses a terminated driver")
    void createChecks() {
        RouteView v = service.create(T, new CreateRouteRequest(DAY, " Morning run ", tanker.getId(), driver.id()), UUID.randomUUID());
        assertEquals("PLANNED", v.state()); assertEquals("Morning run", v.label()); assertEquals("GP 12 AB", v.tankerRegistration());
        verify(compliance).requireCompliantForAllocation(eq(T), eq(tanker.getId()), any(), eq(driver.id()), any());
        EmployeeSummary gone = new EmployeeSummary(UUID.randomUUID(), "E2", "A", "B", "A B", null, null, "Driver", null, "TERMINATED");
        when(hr.findEmployeeById(T, gone.id())).thenReturn(Optional.of(gone));
        assertThrows(IllegalStateException.class, () -> service.create(T, new CreateRouteRequest(DAY, null, tanker.getId(), gone.id()), null));
        assertThrows(ResourceNotFoundException.class, () -> service.create(T, new CreateRouteRequest(DAY, null, UUID.randomUUID(), driver.id()), null));
    }

    @Test @DisplayName("a stop is allocated to the route's tanker and driver and goes last")
    void addStopAllocatesAndAppends() {
        stop(1, 8, "5000");
        FuelDelivery d = load(10, "4000");
        service.addStop(T, route.getId(), add(d));
        var cap = org.mockito.ArgumentCaptor.forClass(AllocationRequest.class);
        verify(transport).allocate(eq(T), eq(d.getId()), cap.capture(), eq(route.getId()));
        assertEquals(tanker.getId(), cap.getValue().tankerId()); assertEquals(driver.id(), cap.getValue().driverEmployeeId());
        assertEquals(route.getId(), d.getRouteId()); assertEquals(2, d.getRouteSequence());
    }

    @Test @DisplayName("a stop must be scheduled, on the route's day, and not already on a route")
    void addStopRules() {
        FuelDelivery routed = load(9, "1000"); routed.joinRoute(UUID.randomUUID(), 1);
        assertThrows(IllegalStateException.class, () -> service.addStop(T, route.getId(), add(routed)));
        FuelDelivery wrongDay = FuelDelivery.create(T, UUID.randomUUID(), UUID.randomUUID(), Map.of(), "DIESEL", bd("1000"), bd("22"),
                DAY.plusDays(1).atTime(9, 0).atZone(SAST).toInstant(), null, null);
        when(deliveries.findActiveByIdForUpdate(eq(T), eq(wrongDay.getId()))).thenReturn(Optional.of(wrongDay));
        assertThrows(IllegalStateException.class, () -> service.addStop(T, route.getId(), add(wrongDay)));
        FuelDelivery onRoad = load(9, "1000"); onRoad.dispatch("D", "V");
        assertThrows(IllegalStateException.class, () -> service.addStop(T, route.getId(), add(onRoad)));
        verify(transport, never()).allocate(any(), any(), any(), any());
    }

    @Test @DisplayName("nothing can be added to or changed on a cancelled route")
    void cancelledRouteClosed() {
        service.cancel(T, route.getId(), "Customer closed for the day");
        assertEquals("CANCELLED", route.getStatus());
        assertThrows(IllegalStateException.class, () -> service.addStop(T, route.getId(), add(load(9, "1000"))));
        assertThrows(IllegalStateException.class, () -> service.reorder(T, route.getId(), List.of()));
        assertThrows(IllegalStateException.class, () -> service.cancel(T, route.getId(), "again"));
    }

    @Test @DisplayName("removing a stop closes the gap in the order and keeps the load allocated")
    void removeClosesGap() {
        FuelDelivery a = stop(1, 8, "1000"), b = stop(2, 9, "1000"), c = stop(3, 10, "1000");
        a.allocate(tanker.getId(), "GP 12 AB", driver.id(), "Moses Sithole");
        when(deliveries.findByRoute(eq(T), eq(route.getId()))).thenAnswer(i -> onRoute.stream().filter(x -> route.getId().equals(x.getRouteId())).sorted(Comparator.comparing(FuelDelivery::getRouteSequence)).toList());
        service.removeStop(T, route.getId(), b.getId());
        assertNull(b.getRouteId());
        assertEquals(1, a.getRouteSequence()); assertEquals(2, c.getRouteSequence());
        assertTrue(a.isAllocated());
    }

    @Test @DisplayName("a stop that is on the road cannot be removed")
    void cannotRemoveOnRoad() {
        FuelDelivery a = stop(1, 8, "1000");
        a.dispatch("D", "V");
        assertThrows(IllegalStateException.class, () -> service.removeStop(T, route.getId(), a.getId()));
        assertEquals(1, a.getRouteSequence());
    }

    @Test @DisplayName("reordering needs every stop once; scheduled stops move, others keep their place")
    void reorderRules() {
        FuelDelivery a = stop(1, 8, "1000"), b = stop(2, 9, "1000"), c = stop(3, 10, "1000");
        assertThrows(IllegalArgumentException.class, () -> service.reorder(T, route.getId(), List.of(a.getId(), b.getId())));
        assertThrows(IllegalArgumentException.class, () -> service.reorder(T, route.getId(), List.of(a.getId(), b.getId(), UUID.randomUUID())));
        service.reorder(T, route.getId(), List.of(c.getId(), a.getId(), b.getId()));
        assertEquals(1, c.getRouteSequence()); assertEquals(2, a.getRouteSequence()); assertEquals(3, b.getRouteSequence());
        // a is now first and goes on the road: it must stay first
        a.moveInRoute(1); b.moveInRoute(2); c.moveInRoute(3);
        a.dispatch("D", "V");
        assertThrows(IllegalStateException.class, () -> service.reorder(T, route.getId(), List.of(b.getId(), a.getId(), c.getId())));
        service.reorder(T, route.getId(), List.of(a.getId(), c.getId(), b.getId()));
        assertEquals(2, c.getRouteSequence());
    }

    @Test @DisplayName("a route with a stop on the road or delivered cannot be cancelled; otherwise the loads leave it, still allocated")
    void cancelRules() {
        FuelDelivery a = stop(1, 8, "1000"), b = stop(2, 9, "1000");
        a.allocate(tanker.getId(), "GP 12 AB", driver.id(), "Moses Sithole");
        a.dispatch("D", "V");
        assertThrows(IllegalStateException.class, () -> service.cancel(T, route.getId(), "Changed our minds"));
        assertTrue(route.isOpen());
        a.complete(bd("1000"), "R", null, null, null, false, null, null);
        assertThrows(IllegalStateException.class, () -> service.cancel(T, route.getId(), "Changed our minds"));
        // a fresh route with only scheduled stops
        onRoute.clear();
        FuelDelivery s = stop(1, 8, "1000");
        s.allocate(tanker.getId(), "GP 12 AB", driver.id(), "Moses Sithole");
        assertThrows(IllegalArgumentException.class, () -> service.cancel(T, route.getId(), " "));
        service.cancel(T, route.getId(), "Customer closed");
        assertNull(s.getRouteId()); assertTrue(s.isAllocated()); assertEquals("CANCELLED", route.getStatus());
    }

    @Test @DisplayName("the route state follows its stops: planned, in progress, completed")
    void stateFollowsStops() {
        assertEquals("PLANNED", FuelRouteService.state(route, List.of(), 0));
        FuelDelivery a = stop(1, 8, "1000"), b = stop(2, 9, "1000");
        assertEquals("PLANNED", FuelRouteService.state(route, onRoute, 0));
        a.dispatch("D", "V");
        assertEquals("IN_PROGRESS", FuelRouteService.state(route, onRoute, 0));
        a.complete(bd("1000"), "R", null, null, null, false, null, null);
        assertEquals("IN_PROGRESS", FuelRouteService.state(route, onRoute, 1));
        b.cancel();
        assertEquals("COMPLETED", FuelRouteService.state(route, onRoute, 1));
    }

    @Test @DisplayName("the day's list shows stops in order with litres planned against the tanker's capacity")
    void listShowsStops() {
        stop(1, 8, "5000"); stop(2, 10, "4000");
        when(routes.findByDate(T, DAY)).thenReturn(List.of(route));
        when(deliveries.findByRoutes(eq(T), any())).thenReturn(new ArrayList<>(onRoute));
        RouteView v = service.list(T, DAY).get(0);
        assertEquals(2, v.stopCount()); assertEquals(0, bd("9000").compareTo(v.plannedLitres())); assertEquals(0, bd("16000").compareTo(v.capacityLitres()));
        assertEquals(List.of(1, 2), v.stops().stream().map(StopView::sequence).toList());
        assertTrue(service.list(T, DAY.plusDays(1)).isEmpty());
    }
}
