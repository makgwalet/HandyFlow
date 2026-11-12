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
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerSite;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerSiteRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.RouteDtos.RouteView;
import za.co.handyflow.platform.fuel.dto.RouteDtos.StopView;
import za.co.handyflow.platform.fuel.dto.RouteDtos.SuggestedOrder;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelRouteOptimiserServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelRouteService routeService;
    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelCustomerSiteRepository sites;
    @InjectMocks FuelRouteOptimiserService service;

    final UUID routeId = UUID.randomUUID();
    final List<StopView> stops = new ArrayList<>();
    final List<FuelDelivery> dels = new ArrayList<>();
    final List<FuelCustomerSite> siteList = new ArrayList<>();

    private void stop(String name, int seq, String status, String lat, String lng) {
        UUID delId = UUID.randomUUID(), siteId = UUID.randomUUID();
        stops.add(new StopView(delId, seq, UUID.randomUUID(), name, "DIESEL", bd("1000"), status, null, null, null, 0));
        FuelDelivery d = mock(FuelDelivery.class); when(d.getId()).thenReturn(delId); when(d.getSiteId()).thenReturn(siteId);
        dels.add(d);
        FuelCustomerSite s = mock(FuelCustomerSite.class);
        when(s.getId()).thenReturn(siteId); when(s.getTenantId()).thenReturn(T);
        when(s.getLatitude()).thenReturn(lat == null ? null : bd(lat)); when(s.getLongitude()).thenReturn(lng == null ? null : bd(lng));
        siteList.add(s);
    }

    @BeforeEach
    void setUp() {
        when(deliveries.findByRoute(eq(T), eq(routeId))).thenAnswer(i -> List.copyOf(dels));
        when(sites.findAllById(any())).thenAnswer(i -> List.copyOf(siteList));
        when(routeService.get(eq(T), eq(routeId))).thenAnswer(i -> view("PLANNED"));
    }

    private RouteView view(String status) {
        return new RouteView(routeId, LocalDate.of(2026, 11, 12), "Run", status, "PLANNED", UUID.randomUUID(), "GP 1", UUID.randomUUID(), "Driver",
                bd("20000"), bd("4000"), stops.size(), 0, null, List.copyOf(stops));
    }

    @Test @DisplayName("with coordinates on every scheduled stop the suggestion is straight-line and lists the new sequence")
    void straightLine() {
        // the delivered first stop is the starting point, so there is one clearly shortest sweep
        stop("Done", 1, "DELIVERED", "-26.0", "28.0"); stop("Far", 2, "SCHEDULED", "-26.0", "28.4"); stop("Near", 3, "SCHEDULED", "-26.0", "28.1"); stop("Mid", 4, "SCHEDULED", "-26.0", "28.2");
        SuggestedOrder o = service.suggest(T, routeId, null, null);
        assertEquals("STRAIGHT_LINE", o.method());
        assertTrue(o.changed());
        assertEquals(List.of("Done", "Near", "Mid", "Far"), o.stops().stream().map(s -> s.customerName()).toList());
        assertEquals(List.of(1, 2, 3, 4), o.stops().stream().map(s -> s.sequence()).toList());
        assertTrue(o.savingKm().signum() > 0);
        assertTrue(o.sitesWithoutPoint().isEmpty());
        assertEquals(FuelRouteOptimiserService.NOTE_STRAIGHT, o.note());
    }

    @Test @DisplayName("a scheduled stop whose site has no coordinates is named and the fallback note is used")
    void missingCoordinatesNamed() {
        stop("Has point", 1, "SCHEDULED", "-26.0", "28.1"); stop("No point", 2, "SCHEDULED", null, null);
        SuggestedOrder o = service.suggest(T, routeId, null, null);
        assertEquals("WINDOWS_AND_DISTANCE", o.method());
        assertEquals(List.of("No point"), o.sitesWithoutPoint());
        assertEquals(FuelRouteOptimiserService.NOTE_FALLBACK, o.note());
        assertNull(o.savingKm());
    }

    @Test @DisplayName("a stop that is already delivered is marked not movable and keeps its place")
    void deliveredStopFixed() {
        stop("Done", 1, "DELIVERED", null, null); stop("B", 2, "SCHEDULED", "-26.0", "28.4"); stop("C", 3, "SCHEDULED", "-26.0", "28.1");
        SuggestedOrder o = service.suggest(T, routeId, null, null);
        assertEquals("Done", o.stops().get(0).customerName());
        assertFalse(o.stops().get(0).movable());
        assertFalse(o.stops().get(0).moved());
    }

    @Test @DisplayName("a half-given or impossible start point is refused")
    void badStartPoint() {
        assertThrows(IllegalArgumentException.class, () -> service.suggest(T, routeId, bd("-26.0"), null));
        assertThrows(IllegalArgumentException.class, () -> service.suggest(T, routeId, bd("95"), bd("28")));
        assertThrows(IllegalArgumentException.class, () -> service.suggest(T, routeId, bd("-26"), bd("200")));
    }

    @Test @DisplayName("a cancelled route has nothing to order")
    void cancelled() {
        when(routeService.get(eq(T), eq(routeId))).thenAnswer(i -> view("CANCELLED"));
        assertThrows(IllegalStateException.class, () -> service.suggest(T, routeId, null, null));
    }

    @Test @DisplayName("a site that belongs to another tenant is ignored")
    void otherTenantSiteIgnored() {
        stop("A", 1, "SCHEDULED", "-26.0", "28.4"); stop("B", 2, "SCHEDULED", "-26.0", "28.1");
        FuelCustomerSite foreign = siteList.get(1);
        when(foreign.getTenantId()).thenReturn(TenantId.of(UUID.randomUUID()));
        SuggestedOrder o = service.suggest(T, routeId, null, null);
        assertEquals("WINDOWS_AND_DISTANCE", o.method());
        assertTrue(o.sitesWithoutPoint().contains("B"));
    }
}
