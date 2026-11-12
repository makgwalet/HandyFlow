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
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.LandedCostDtos.Report;
import za.co.handyflow.platform.fuel.dto.LandedCostDtos.RouteRow;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Phase 6c: landed cost = cost at sale + recorded transport; only complete routes feed per-litre figures and rollups. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelLandedCostServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID BY = UUID.randomUUID();
    static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelRouteRepository routes;
    @Mock FuelRouteCostEntryRepository entries;
    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelTankerRepository tankers;
    @Mock FuelTankRepository tanks;
    @Mock FuelDepotRepository depots;
    @InjectMocks FuelLandedCostService service;

    FuelRoute route; FuelTanker tanker;
    final List<FuelRouteCostEntry> actuals = new ArrayList<>();
    final UUID depotA = UUID.randomUUID(), depotB = UUID.randomUUID();
    FuelTank tankA, tankB;

    @BeforeEach
    void setUp() {
        tanker = FuelTanker.create(T, "GP 12 AB", null);
        route = FuelRoute.create(T, DAY, "Morning run", tanker.getId(), UUID.randomUUID(), BY);
        tankA = mock(FuelTank.class); when(tankA.getId()).thenReturn(UUID.randomUUID()); when(tankA.getDepotId()).thenReturn(depotA);
        tankB = mock(FuelTank.class); when(tankB.getId()).thenReturn(UUID.randomUUID()); when(tankB.getDepotId()).thenReturn(depotB);
        FuelDepot dA = mock(FuelDepot.class); when(dA.getId()).thenReturn(depotA); when(dA.getName()).thenReturn("North depot");
        FuelDepot dB = mock(FuelDepot.class); when(dB.getId()).thenReturn(depotB); when(dB.getName()).thenReturn("South depot");
        when(tanks.findAllActive(T)).thenReturn(List.of(tankA, tankB));
        when(depots.findAllActive(T)).thenReturn(List.of(dA, dB));
        when(tankers.findAllActive(T)).thenReturn(List.of(tanker));
        when(routes.findBetween(eq(T), any(), any())).thenReturn(List.of(route));
        when(entries.findActiveByRoutes(eq(T), any())).thenAnswer(i -> actuals.stream().filter(FuelRouteCostEntry::isActive).toList());
    }

    /** Delivered at R22 a litre; cost at sale as given (null = none recorded). */
    private FuelDelivery delivered(FuelTank tank, String litres, String cost) {
        FuelDelivery d = FuelDelivery.create(T, tank.getId(), UUID.randomUUID(), Map.of(), "DIESEL", bd(litres), bd("22"), Instant.now(), null, null);
        d.joinRoute(route.getId(), 1);
        d.dispatch("D", "V");
        d.complete(bd(litres), "R", null, null, null, false, null, null);
        if (cost != null) d.recordCostSnapshot(bd(cost));
        return d;
    }

    private void withStops(FuelDelivery... ds) { when(deliveries.findByRoutes(eq(T), any())).thenReturn(List.of(ds)); }
    private FuelRouteCostEntry entry(String category, String amount) { FuelRouteCostEntry e = FuelRouteCostEntry.create(T, route.getId(), category, bd(amount), null, null, "x", BY); actuals.add(e); return e; }

    @Test @DisplayName("landed cost per litre = (cost at sale x litres + every recorded transport cost) / litres, and margin per litre is revenue less that")
    void landedCost() {
        withStops(delivered(tankA, "3000", "18"), delivered(tankA, "5000", "18"));
        entry("VEHICLE", "1200"); entry("DRIVER", "600"); entry("TOLLS", "200");
        Report r = service.report(T, DAY, DAY);
        RouteRow row = r.routes().get(0);
        assertTrue(row.complete());
        assertEquals(0, bd("8000.00").compareTo(row.litres()));
        assertEquals(0, bd("176000.00").compareTo(row.revenue()));         // 8000 x R22
        assertEquals(0, bd("144000.00").compareTo(row.productCost()));     // 8000 x R18
        assertEquals(0, bd("2000.00").compareTo(row.transportCost()));
        assertEquals(0, bd("146000.00").compareTo(row.landedCost()));
        assertEquals(0, bd("18.2500").compareTo(row.landedPerLitre()));    // 146000 / 8000
        assertEquals(0, bd("0.2500").compareTo(row.transportPerLitre()));
        assertEquals(0, bd("3.7500").compareTo(row.marginPerLitre()));     // 22 - 18.25
        assertEquals(0, bd("30000.00").compareTo(row.margin()));
        assertEquals(0, bd("30000.00").compareTo(r.total().margin())); assertEquals(1, r.total().routes()); assertEquals(0, r.routesLeftOut());
    }

    @Test @DisplayName("a route with no vehicle or driver cost recorded is shown and flagged but gives no per-litre figure and is left out of the rollups")
    void noTransportRecorded() {
        withStops(delivered(tankA, "3000", "18"));
        entry("TOLLS", "200");                                              // tolls alone do not make a route's carry cost known
        Report r = service.report(T, DAY, DAY);
        RouteRow row = r.routes().get(0);
        assertFalse(row.complete()); assertTrue(row.flags().contains("NO_TRANSPORT_COST"));
        assertNull(row.landedPerLitre()); assertNull(row.marginPerLitre()); assertNull(row.landedCost());
        assertEquals(1, r.routesLeftOut()); assertEquals(0, r.total().routes()); assertNull(r.total().landedPerLitre());
        assertTrue(r.byTanker().isEmpty()); assertTrue(r.byDepot().isEmpty());
    }

    @Test @DisplayName("a delivery with no cost at sale makes the route incomplete; the cost is never guessed")
    void noProductCost() {
        withStops(delivered(tankA, "3000", "18"), delivered(tankA, "1000", null));
        entry("VEHICLE", "500");
        RouteRow row = service.report(T, DAY, DAY).routes().get(0);
        assertFalse(row.complete()); assertTrue(row.flags().contains("NO_PRODUCT_COST")); assertNull(row.productPerLitre()); assertNull(row.productCost());
    }

    @Test @DisplayName("a partly delivered route is flagged and left out, because the carry cost covers the whole trip")
    void partialDelivery() {
        FuelDelivery notYet = FuelDelivery.create(T, tankA.getId(), UUID.randomUUID(), Map.of(), "DIESEL", bd("2000"), bd("22"), Instant.now(), null, null);
        notYet.joinRoute(route.getId(), 2);
        withStops(delivered(tankA, "3000", "18"), notYet);
        entry("VEHICLE", "500");
        Report r = service.report(T, DAY, DAY);
        assertTrue(r.routes().get(0).flags().contains("PARTIAL_DELIVERY")); assertFalse(r.routes().get(0).complete()); assertEquals(1, r.routesLeftOut());
    }

    @Test @DisplayName("a voided cost entry does not count")
    void voidedIgnored() {
        withStops(delivered(tankA, "1000", "18"));
        entry("VEHICLE", "500");
        FuelRouteCostEntry wrong = entry("VEHICLE", "9000");
        wrong.voidEntry("typed twice", BY);
        assertEquals(0, bd("500.00").compareTo(service.report(T, DAY, DAY).routes().get(0).transportCost()));
    }

    @Test @DisplayName("by depot, a route's figures are shared by litres delivered from each depot's tanks; by tanker they stay whole")
    void depotShares() {
        withStops(delivered(tankA, "6000", "18"), delivered(tankB, "2000", "20"));
        entry("VEHICLE", "800");
        Report r = service.report(T, DAY, DAY);
        assertEquals(1, r.byTanker().size()); assertEquals("GP 12 AB", r.byTanker().get(0).label());
        assertEquals(2, r.byDepot().size());
        var north = r.byDepot().stream().filter(x -> x.label().equals("North depot")).findFirst().orElseThrow();
        var south = r.byDepot().stream().filter(x -> x.label().equals("South depot")).findFirst().orElseThrow();
        assertEquals(0, bd("6000.00").compareTo(north.litres())); assertEquals(0, bd("2000.00").compareTo(south.litres()));
        assertEquals(0, bd("600.00").compareTo(north.transportCost()));    // 75% of R800
        assertEquals(0, bd("200.00").compareTo(south.transportCost()));
        assertEquals(0, r.total().landedCost().compareTo(north.landedCost().add(south.landedCost())));
    }

    @Test @DisplayName("a start after the end, or a period over a year, is refused; no routes gives an empty report")
    void periodRules() {
        assertThrows(IllegalArgumentException.class, () -> service.report(T, DAY.plusDays(1), DAY));
        assertThrows(IllegalArgumentException.class, () -> service.report(T, DAY.minusDays(400), DAY));
        when(routes.findBetween(eq(T), any(), any())).thenReturn(List.of());
        Report r = service.report(T, DAY, DAY);
        assertTrue(r.routes().isEmpty()); assertEquals(0, r.routesWithDeliveries());
    }
}
